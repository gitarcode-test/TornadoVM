package tornado.drivers.opencl.graal.phases;

import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.graph.Node;
import com.oracle.graal.loop.LoopEx;
import com.oracle.graal.loop.LoopsData;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.AddNode;
import com.oracle.graal.nodes.calc.IntegerLessThanNode;
import com.oracle.graal.nodes.calc.MulNode;
import com.oracle.graal.phases.BasePhase;
import java.util.List;
import jdk.vm.ci.meta.JavaKind;
import tornado.common.Tornado;
import tornado.drivers.opencl.enums.OCLDeviceType;
import tornado.drivers.opencl.graal.meta.Coarseness;
import tornado.drivers.opencl.graal.nodes.GlobalThreadIdNode;
import tornado.drivers.opencl.graal.nodes.GlobalThreadSizeNode;
import tornado.drivers.opencl.runtime.OCLDeviceMapping;
import tornado.graal.nodes.ParallelOffsetNode;
import tornado.graal.nodes.ParallelRangeNode;
import tornado.graal.nodes.ParallelStrideNode;
import tornado.graal.phases.TornadoHighTierContext;
import tornado.meta.Meta;
import tornado.meta.domain.DomainTree;
import tornado.meta.domain.IntDomain;

import static tornado.common.Tornado.USE_THREAD_COARSENING;
import static tornado.common.Tornado.debug;
import static tornado.common.exceptions.TornadoInternalError.guarantee;
import static tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;
import static tornado.common.exceptions.TornadoUnsupportedError.unsupported;

public class OCLThreadCoarsener extends BasePhase<TornadoHighTierContext> {

    private ParallelRangeNode findParallelRange(LoopBeginNode loopBegin) {

        IfNode ifNode = null;
        FixedWithNextNode current = loopBegin;
        while (current != null) {

            FixedNode next = current.next();
            if (next instanceof IfNode) {
                ifNode = (IfNode) next;
                break;
            } else if (next instanceof FixedWithNextNode) {
                current = (FixedWithNextNode) next;
            } else {
                shouldNotReachHere();
            }
        }

        guarantee(ifNode != null, "invalid loop header");
        return ifNode.condition().inputs().filter(ParallelRangeNode.class).first();
    }

    @Override
    protected void run(StructuredGraph graph, TornadoHighTierContext context) {
        if (!USE_THREAD_COARSENING || !context.hasDeviceMapping() || !context.getMeta().hasDomain()) {
            return;
        }

        Meta meta = context.getMeta();
        DomainTree domain = meta.getDomain();

        if (domain.getDepth() == 0) {
            return;
        }

        Coarseness coarseness;
        if (meta.hasProvider(Coarseness.class)) {
            coarseness = meta.getProvider(Coarseness.class);
        } else {
            coarseness = new Coarseness(domain.getDepth());
        }

        OCLDeviceMapping mapping = (OCLDeviceMapping) context.getDeviceMapping();
        if (mapping.getDevice().getDeviceType() == OCLDeviceType.CL_DEVICE_TYPE_CPU) {
            int[] config = new int[3];
            String configCpu = Tornado.getProperty("tornado.opencl.cpu.config");
            if (configCpu != null) {
                int index = 0;
                for (String str : configCpu.split(",")) {
                    config[index] = Integer.parseInt(str);
                    index++;
                }
            } else {
                config[0] = mapping.getDevice().getMaxComputeUnits();
                config[1] = 1;
                config[2] = 1;
            }

            long[] workItems = mapping.getDevice().getMaxWorkItemSizes();
            for (int i = 0; i < domain.getDepth(); i++) {
                if (workItems[i] > 1) {
                    coarseness.setCoarseness(i, domain.get(i).cardinality() / config[i]);
                } else {
                    coarseness.setCoarseness(i, 1);
                }
            }
        }

        debug("coarseness: %s", coarseness);

        if (graph.hasLoops()) {
            final LoopsData data = new LoopsData(graph);
            data.detectedCountedLoops();

            List<LoopEx> loops = data.outerFirst();
            for (LoopEx l : loops) {
                ParallelRangeNode rangeNode = findParallelRange(l.loopBegin());
                if (rangeNode != null) {
                    coarsen(graph, l, rangeNode, coarseness, domain);
                }
            }
            graph.clearLastSchedule();

        }
    }

    private ValueNode getOp(ParallelStrideNode stride) {
        for (Node use : stride.usages()) {
            if (use instanceof ParallelRangeNode) {
                continue;
            } else {
                return (ValueNode) use;
            }
        }
        return null;
    }

    private IfNode findLastIfNode(FixedNode node) {
        FixedNode current = (FixedNode) node.predecessor();
        while (current != null && !(current instanceof IfNode)) {
            current = (FixedNode) current.predecessor();
        }

        return (current instanceof IfNode) ? (IfNode) current : null;
    }

    private void insertInnerLoop(StructuredGraph graph, LoopBeginNode oldLoopBegin, PhiNode oldIv, ParallelRangeNode range, ValueNode coarseness, ValueNode stride) {

        guarantee(oldLoopBegin.loopExits().count() == 1, "multiple loop exists");
        LoopExitNode oldLoopExit = oldLoopBegin.loopExits().first();

        guarantee(oldLoopBegin.getLoopEndCount() == 1, "multiple loop ends");
        LoopEndNode oldLoopEnd = oldLoopBegin.loopEnds().first();

        IfNode oldLoopCondition = findLastIfNode(oldLoopExit);
        guarantee(oldLoopCondition != null, "could not find old loop condition");

        LoopBeginNode newLoopBegin = graph.addWithoutUnique(new LoopBeginNode());
        LoopExitNode newLoopExit1 = graph.addWithoutUnique(new LoopExitNode(newLoopBegin));
        LoopExitNode newLoopExit2 = graph.addWithoutUnique(new LoopExitNode(newLoopBegin));

        ValuePhiNode newPhi = graph.addOrUnique(new ValuePhiNode(StampFactory.forKind(JavaKind.Int), newLoopBegin));

        oldIv.replaceAtMatchingUsages(newPhi, node -> {
            return node.getId() != range.usages().first().getId() && node.getId() != oldIv.valueAt(1).getId();
        });

        AddNode newStride = graph.addOrUnique(new AddNode(newPhi, stride));
        newPhi.initializeValueAt(0, oldIv);
        newPhi.initializeValueAt(1, newStride);

        ValueNode coarsenessLimit = graph.addOrUnique(new AddNode(oldIv, coarseness));

        LogicNode lessThanCoarsenessLimit = graph.addOrUnique(new IntegerLessThanNode(newPhi, coarsenessLimit));

        /*
         * loop condition to check we are still in range
         */
        LogicNode lessThanOriginalRange = graph.addOrUnique(new IntegerLessThanNode(newPhi, range));
        AbstractBeginNode trueSuccessor = oldLoopCondition.trueSuccessor();

        BeginNode innerBegin = graph.addWithoutUnique(new BeginNode());
        IfNode newLoopIf = graph.addWithoutUnique(new IfNode(lessThanOriginalRange, innerBegin, newLoopExit1, .95));
        newLoopBegin.setNext(newLoopIf);

        /*
         * insert check to ensure we are within coarseness limits
         */
        LoopEndNode newLoopEnd2 = graph.addWithoutUnique(new LoopEndNode(oldLoopBegin));
        newLoopExit2.setNext(newLoopEnd2);
        oldIv.initializeValueAt(2, oldIv.valueAt(1));

        IfNode innerIf = graph.addWithoutUnique(new IfNode(lessThanCoarsenessLimit, trueSuccessor, newLoopExit2, .95));
        innerBegin.setNext(innerIf);
        BeginNode newBegin = graph.addWithoutUnique(new BeginNode());
        EndNode newEnd = graph.addWithoutUnique(new EndNode());
        newBegin.setNext(newEnd);
        newLoopBegin.addForwardEnd(newEnd);

        /*
         * update condition on old loop to point to inner loop
         */
        oldLoopCondition.setTrueSuccessor(newBegin);

        LoopEndNode newLoopEnd1 = graph.addWithoutUnique(new LoopEndNode(newLoopBegin));

        oldLoopEnd.replaceAtPredecessor(newLoopEnd1);
        graph.addAfterFixed(newLoopExit1, oldLoopEnd);
    }

    private void coarsen(StructuredGraph graph, LoopEx loop, ParallelRangeNode range, Coarseness coarseness, DomainTree domain) {
        ParallelOffsetNode offset = range.offset();
        ParallelStrideNode stride = range.stride();

        guarantee(offset.usages().first() instanceof PhiNode, "unable to find original phi");
        PhiNode originalIV = (PhiNode) offset.usages().first();

        ValueNode opNode = getOp(stride);
        if (!(opNode instanceof AddNode)) {
            unsupported("parallel loop uses unsupoorted op: %s", opNode);
        }

        final ValueNode desiredCoarseness = ConstantNode.forInt(coarseness.getCoarseness(range.index()), graph);
        final ValueNode updatedStride = graph.addOrUnique(new MulNode(stride.value(), desiredCoarseness));
        final ConstantNode index = graph.addOrUnique(ConstantNode.forInt(offset.index()));
        final GlobalThreadIdNode threadId = graph.addOrUnique(new GlobalThreadIdNode(index));
        final ValueNode x1 = graph.addOrUnique(new MulNode(threadId, updatedStride));
        final AddNode x0 = graph.addOrUnique(new AddNode(x1, offset.value()));
        final GlobalThreadSizeNode threadCount = graph.addOrUnique(new GlobalThreadSizeNode(index));
        final ValueNode updatedStride1 = graph.addOrUnique(new MulNode(threadCount, updatedStride));

        // remove parallel placeholders
        offset.replaceAtUsages(x0);
        stride.replaceAtUsages(updatedStride1);
        Debug.dump(Debug.BASIC_LOG_LEVEL, graph, "after re-writing=" + range.index());

        if (coarseness.getCoarseness(range.index()) > 1) {
            insertInnerLoop(graph, loop.loopBegin(), originalIV, range, desiredCoarseness, stride.value());
            /*
             * update domain tree - need to change
             */
            IntDomain cr = (IntDomain) domain.get(range.index());
            int size = (cr.getLength() - cr.getOffset()) / cr.getStep();
            size = (size + coarseness.getCoarseness(range.index()) - 1) / coarseness.getCoarseness(range.index());
            cr.setOffset(0);
            cr.setLength(size);
            cr.setStep(1);
        } else {

//            /*
//             * update domain tree - need to change
//             */
//            IntDomain cr = (IntDomain) domain.get(range.index());
//            cr.setOffset(0);
//            cr.setLength(1);
//            cr.setStep(1);
        }

        offset.safeDelete();
        stride.safeDelete();
        range.replaceAndDelete(range.value());

        Debug.dump(Debug.BASIC_LOG_LEVEL, graph, "after coarsening index=" + range.index());
    }

}
