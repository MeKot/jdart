/*
 * Copyright (C) 2015, United States Government, as represented by the
 * Administrator of the National Aeronautics and Space Administration.
 * All rights reserved.
 *
 * The PSYCO: A Predicate-based Symbolic Compositional Reasoning environment
 * platform is licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You may obtain a
 * copy of the License at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package gov.nasa.jpf.jdart.constraints;

import ctrie.CTrieMap;
import ctrie.CoordinatorCTrie;
import gov.nasa.jpf.JPF;
import gov.nasa.jpf.constraints.api.ConstraintSolver.Result;
import gov.nasa.jpf.constraints.api.Expression;
import gov.nasa.jpf.constraints.api.SolverContext;
import gov.nasa.jpf.constraints.api.Valuation;
import gov.nasa.jpf.constraints.util.ExpressionUtil;
import gov.nasa.jpf.jdart.config.AnalysisConfig;
import gov.nasa.jpf.jdart.config.ConcolicValues;
import gov.nasa.jpf.util.JPFLogger;
import gov.nasa.jpf.util.Pair;
import gov.nasa.jpf.vm.Instruction;
import seedbag.BatchedBlockingQueue;
import seedbag.CoordinatorSeedBag;

import java.util.*;

public class InternalConstraintsTree {


    public enum BranchEffect {
        NORMAL,
        UNEXPECTED,
        INCONCLUSIVE
    }

    private static abstract class NodeData {
    }

    private static final class DecisionData extends NodeData {
        private final Node node;
        private final Instruction branchInsn;
        private final Expression<Boolean>[] constraints;
        private final Node[] children;
        private int numOpen;
        private int numUnexhausted;


        public DecisionData(Node node, Instruction branchInsn, Expression<Boolean>[] constraints, boolean explore) {
            this.node = node;
            this.branchInsn = branchInsn;
            this.constraints = constraints;
            this.children = new Node[constraints.length];
            this.numUnexhausted = constraints.length;

            if (!explore) {
                for (int i = 0; i < constraints.length; i++) {
                    this.children[i] = new Node(node);
                    this.children[i].dontKnow();
                }
                this.numOpen = 0;
            } else {
                this.numOpen = constraints.length;
            }
        }

        public Expression<Boolean> getConstraint(int idx) {
            return constraints[idx];
        }

        public Node getChild(int idx) {
            Node c = children[idx];
            if (c == null) {
                c = new Node(node);
                children[idx] = c;
            }
            return c;
        }

        public boolean hasUnexhausted() {
            return (numUnexhausted > 0);
        }

        public boolean hasOpen() {
            return (numOpen > 0);
        }

        public int nextOpenChild() {
            if (numOpen == 0)
                return -1;

            for (int i = 0; i < constraints.length; i++) {
                Node n = children[i];
                if (n == null || n.isOpen())
                    return i;
            }

            return -1;
        }

        public void decrementOpen() {
            numOpen--;
        }

        public void decrementUnexhausted() {
            numUnexhausted--;
        }

        public void verifyDecision(Instruction branchInsn, Expression<Boolean>[] constraints) {
            if (branchInsn != this.branchInsn)
                throw new IllegalStateException("Same decision, but different branching instruction!");
            if (constraints != null && constraints.length == this.constraints.length)
                throw new IllegalStateException("Same decision, but different number of constraints!");
        }

    }

    private static abstract class AbstractResultData extends NodeData {

        public abstract PathResult getResult();

    }

    private static final class ResultData extends AbstractResultData {

        private final PathResult result;

        public ResultData(PathResult result) {
            this.result = result;
        }

        @Override
        public PathResult getResult() {
            return result;
        }

    }

    private static final class DontKnowData extends AbstractResultData {

        private static final DontKnowData INSTANCE = new DontKnowData();

        public static DontKnowData getInstance() {
            return INSTANCE;
        }

        @Override
        public PathResult getResult() {
            return PathResult.dontKnow();
        }

    }

    private static final class UnsatisfiableData extends NodeData {
        private static final UnsatisfiableData INSTANCE = new UnsatisfiableData();

        public static UnsatisfiableData getInstance() {
            return INSTANCE;
        }
    }

    private static final class Node {
        private final Node parent;
        private final int depth;
        private int altDepth;

        private NodeData data;


        public Node(Node parent) {
            this.parent = parent;
            this.depth = (parent != null) ? parent.depth + 1 : 0;
            this.altDepth = (parent != null) ? parent.altDepth : 0;
        }

        public int incAltDepth() {
            return ++altDepth;
        }

        public boolean isVirgin() {
            return (data == null);
        }


        public Node getParent() {
            return parent;
        }


        public int getDepth() {
            return depth;
        }

        public boolean hasData() {
            return (data != null);
        }

        public DecisionData decisionData() {
            if (data == null || data.getClass() != DecisionData.class)
                return null;
            return (DecisionData) data;
        }

        public boolean isOpen() {
            DecisionData dec = decisionData();
            if (dec == null)
                return (data == null);
            return dec.hasOpen();
        }

        public boolean isExhausted() {
            DecisionData dec = decisionData();
            if (dec == null)
                return (data != null && data.getClass() != DontKnowData.class); // Dont know is not exhausted, all other forms of data are

            return !dec.hasUnexhausted();
        }

        public boolean hasDecisionData() {
            if (data == null || data.getClass() == DontKnowData.class)
                return false;

            //if(data.getClass() == DecisionData.class)
            return true;
            //throw new IllegalArgumentException("Querying non-decision node (depth: "+ depth +
            //        ") about decision data! " + data.getClass());
        }

        public DecisionData decision(Instruction branchInsn, Expression<Boolean>[] constraints, boolean explore) {
            if (!hasDecisionData()) {
                DecisionData dec = new DecisionData(this, branchInsn, constraints, explore);
                data = dec;
                return dec;
            }

            DecisionData dec = (DecisionData) data;
            dec.verifyDecision(branchInsn, constraints);

            return dec;
        }

        public ResultData result(PathResult result) {
            if (data == null || data.getClass() == DontKnowData.class) {
                ResultData res = new ResultData(result);
                data = res;
                return res;
            }

            //throw new IllegalStateException("Attempting to finish already explored path (data = " + data.getClass().getName() + "!");
            return null;
        }

        public DontKnowData dontKnow() {
            if (data == null) {
                DontKnowData dk = DontKnowData.getInstance();
                data = dk;
                return dk;
            }

            if (data.getClass() != DontKnowData.class) {
                //System.err.println("Attempting to fail already explored path!");
                return null;
            }
            return (DontKnowData) data;
        }

        public UnsatisfiableData unsatisfiable() {
            if (data == null) {
                UnsatisfiableData dk = UnsatisfiableData.getInstance();
                data = dk;
                return dk;
            }

            //throw new IllegalStateException("Attempting to fail already explored path!");
            return null;
        }

    }

    private final JPFLogger logger = JPF.getLogger("jdart");

    static final BatchedBlockingQueue<Object[]> seedBag = new CoordinatorSeedBag<>("localhost", 8080);
    static final CTrieMap<String, Integer> cTrieMap = new CoordinatorCTrie<>("localhost", 8080);
    static final Set<String> nextPaths = new HashSet<>();

    private static int N = 5;

    private final Node root = new Node(null);
    private Node current = root; // This is the current node in our EXPLORATION
    private Node currentTarget = root; // This is the node the valuation computed by the constraint solver SHOULD reach

    private final AnalysisConfig anaConf;

    private ArrayList<Integer> expectedPath = new ArrayList<>();
    private boolean diverged = false;
    private final SolverContext solverCtx;
    private boolean explore;

    private final ConcolicValues preset;
    private boolean replay = false;

    private Valuation prev = null;


    private Object[] valuationToObjectArray(Valuation valuation) {
        List<Object> values = new ArrayList<>();
        values.add(0);
        values.add(0);
        values.add(0);
        valuation.iterator().forEachRemaining(x -> {
            switch (x.getVariable().getName()) {
                case "n":
                    values.set(0, x.getValue());
                    break;
                case "m":
                    values.set(1, x.getValue());
                    break;
                case "x":
                    values.set(2, x.getValue());
                    break;
            }
        });
        return values.toArray();
    }


    public InternalConstraintsTree(SolverContext solverCtx, AnalysisConfig anaConf) {
        this(solverCtx, anaConf, null);
    }

    public InternalConstraintsTree(SolverContext solverCtx, AnalysisConfig anaConf, ConcolicValues preset) {
        this.solverCtx = solverCtx;
        this.anaConf = anaConf;
        this.explore = anaConf.isExploreInitially();
        this.preset = preset;


    }

    public void setExplore(boolean explore) {
        this.explore = explore;
    }

    public boolean needsDecision() {
        return !current.hasDecisionData();
    }

    /**
     * Retrieves the node in the constraints tree that would be reached using
     * the given valuation.
     *
     * @param valuation the valuation
     * @return the node in the tree that would be reached by the given valuation
     */
    public Node simulate(Valuation valuation) {
        Node curr = root;

        while (curr.decisionData() != null) {
            DecisionData dd = curr.decisionData();
            int branchIdx = -1;
            for (int i = 0; i < dd.constraints.length; i++) {
                Expression<Boolean> constraint = dd.constraints[i];
                try {
                    if (constraint.evaluate(valuation)) {
                        branchIdx = i;
                        break;
                    }
                } catch (RuntimeException ex) {
                    // e.g. due to function with undefined semantics
                    return null;
                }
            }
            if (branchIdx < 0) {
                throw new IllegalStateException("Non-complete set of constraints at constraints tree node!");
            }
            if (dd.children[branchIdx] == null) {
                break;
            }
            curr = dd.children[branchIdx];
        }

        return curr;
    }

    public BranchEffect decision(Instruction insn, int branchIdx, Expression<Boolean>[] decisions) {
        if (anaConf.maxDepthExceeded(current.depth)) {
            //System.err.println("DEPTH EXCEEDED");
            return BranchEffect.NORMAL; // just ignore it
        }

        DecisionData data;
        try {
            data = current.decision(insn, decisions, explore);
        } catch (IllegalStateException e) {
            logger.severe(e.getMessage());
            // FIXME: this indicates a bug //
            return BranchEffect.INCONCLUSIVE;
        }

        int depth = current.getDepth();
        current = data.getChild(branchIdx);

        if (current.isExhausted() && !replay) { // FALK: check how exhaustion is computed, maybe replay check is not necessary
            diverged = true;
            return BranchEffect.INCONCLUSIVE;
        }

        if (!diverged) {
            if (depth < expectedPath.size()) {
                int expected = expectedPath.get(depth).intValue();
                if (expected != branchIdx) {
                    diverged = true;
                    return BranchEffect.UNEXPECTED;
                }
            } else {
                Expression<Boolean> constraint = data.getConstraint(branchIdx);
                solverCtx.push();
                try {
                    solverCtx.add(constraint);
                } catch (RuntimeException ex) {
                    logger.finer(ex.getMessage());
                    //ex.printStackTrace();
                }

                expectedPath.add(branchIdx);
                currentTarget = current;
            }
        }

        return BranchEffect.NORMAL;
    }

    public void finish(PathResult result) {
        current.result(result);
    }


    public void failCurrentTarget() {
        currentTarget.dontKnow();
    }

    int[] traceToBitVector(String trace) {
        int[] ret = new int[trace.length()];

        for (int x = 0; x < trace.length(); x++) {
            ret[x] = trace.charAt(x) - '0';
        }
        return ret;
    }


    private Node backtrack(Node node, boolean pop) {
        if (node == null)
            return null;

        while (!node.isOpen()) {
            boolean exh = node.isExhausted();
            node = node.getParent();
            if (node == null)
                break;
            if (pop) {
                solverCtx.pop();
                expectedPath.remove(expectedPath.size() - 1);
            }
            DecisionData dec = node.decisionData();
            dec.decrementOpen();
            if (exh)
                dec.decrementUnexhausted();
        }

        return node;
    }

    public Valuation findNext() {
        replay = false;

        if (diverged) {
            backtrack(current, false);
            diverged = false;
        }
        current = root;

        while (nextPaths.isEmpty()) {
            nextPaths.addAll(cTrieMap.getNextNPaths(N));
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        int[] decisionTrace = traceToBitVector(nextPaths.iterator().next());

        System.out.println(Arrays.toString(decisionTrace));

        while ((currentTarget = backtrack(currentTarget, true)) != null) {
            DecisionData dec = currentTarget.decisionData();
            if (dec == null) {
                assert currentTarget.isVirgin();
                int ad = currentTarget.incAltDepth();
                if (anaConf.maxAltDepthExceeded(ad) || anaConf.maxDepthExceeded(currentTarget.depth)) {
                    currentTarget.dontKnow();
                    continue;
                }
                // (guided JDart execution) check if the current trace is a prefix of the target decision trace

                int[] currentTrace = tracePathToNode(currentTarget);

                if (logger.isFineLogged()) {
                    logger.fine("target  trace: ", Arrays.toString(decisionTrace));
                    logger.fine("current trace: ", Arrays.toString(currentTrace));
                }
                int size = Math.min(decisionTrace.length, currentTrace.length);
                boolean isPrefix = true;
                for (int i = 0; i < size; i++) {
                    if (decisionTrace[i] != currentTrace[i]) {
                        isPrefix = false;
                        break;
                    }
                }
//                for (int i = 0; i < size && isPrefix; i++) {
//                  isPrefix = decisionTrace[i] == currentTrace[i];
//                }
                if (!isPrefix) {
                    logger.fine("skipping node: ", currentTarget);
                    currentTarget.dontKnow();
                    continue;
                }
                if (currentTrace.length >= decisionTrace.length) {
                    logger.fine("Target path found! continuing...");
                    Valuation val = new Valuation();
                    Result res = solverCtx.solve(val);
                    nextPaths.remove(traceToString(decisionTrace));
                    seedBag.add(valuationToObjectArray(val));
                    logger.finer("Found valuation for seed: " + Arrays.toString(decisionTrace));
                    return ExpressionUtil.combineValuations(val);
                } else {
                    logger.fine("prefix found! continuing...");
                }

                Valuation val = new Valuation();
                Result res = solverCtx.solve(val);

                // FIXME: prevent generation of valuation that has been used before.
                switch (res) {
                    case UNSAT:
                        currentTarget.unsatisfiable();
                        System.out.println("Unsatisfiable");
                        break;
                    case DONT_KNOW:
                        currentTarget.dontKnow();
                        break;
                    case SAT:
                        Node predictedTarget = simulate(val);
                        if (predictedTarget != null && predictedTarget != currentTarget) {
                            boolean inconclusive = predictedTarget.isExhausted();
                            logger.info("Predicted ", inconclusive ? "inconclusive " : "", "divergence");
                            if (inconclusive) {
                                logger.finer("NOT attempting execution");
                                currentTarget.dontKnow();
                                break;
                            }
                        }
                        if (val.equals(prev)) {
                            logger.finer("Wont re-execute with known valuation");
                            currentTarget.dontKnow();
                            break;
                        }

                        val.getVariables().forEach(x-> System.out.println(x.toString()));
                        System.out.println(Arrays.deepToString(valuationToObjectArray(val)));
                        seedBag.add(valuationToObjectArray(val));
                        nextPaths.remove(traceToString(decisionTrace));

                        prev = val;
                        return ExpressionUtil.combineValuations(val);
                }
            } else {
                int nextIdx = dec.nextOpenChild();
                assert (nextIdx != -1);
                Expression<Boolean> constraint = dec.getConstraint(nextIdx);
                Node c = dec.getChild(nextIdx);
                currentTarget = c;
                solverCtx.push();
                expectedPath.add(nextIdx);
                try {
                    solverCtx.add(constraint);
                } catch (Exception ex) {
                    logger.finer(ex.getMessage());
                    // ex.printStackTrace();
                    //currentTarget.dontKnow(); // TODO good idea?
                }
            }
        }

        //We fall back on the preset values that might be specified in the
        //jpf config -- this only happens when we cannot find a new target
        //node from exercising the constraints tree
        if (preset != null && preset.hasNext()) {
            current = root;
            currentTarget = root;
            assert this.expectedPath.isEmpty();
            replay = true;
            return preset.next();
        }

        System.out.println("Could not find a valuation for trace " + Arrays.toString(decisionTrace));
        nextPaths.remove(traceToString(decisionTrace));
        return null;
    }

    private int[] tracePathToNode(Node target) {
        int depth = target.depth;
        int[] trace = new int[depth];
        Node current = target;

        for (int i = depth - 1; i >= 0; i--) {
            Node parent = current.getParent();
            DecisionData decisions = parent.decisionData();
            int choice = 0;
            for (Node child : decisions.children) {
                //not sure why child can be null here - maybe the array is populated lazily?
                if (child != null && child.equals(current)) {
                    break;
                }
                choice++;
            }
            if (choice > decisions.children.length) {
                throw new RuntimeException("Is this really the parent for this node? " + parent);
            }
            trace[i] = choice;
            current = parent;
        }

        return trace;
    }

    public ConstraintsTree toFinalCTree() {
        if (root == null) {
            return null;
        }
        TrimmedConstraintsTree.Node r = trim();
        if (r == null) {
            return null;
        }
        return new ConstraintsTree(TrimmedConstraintsTree.toBinaryCTree(r));
    }

    TrimmedConstraintsTree.Node trim() {
        LinkedList<Pair<Integer, TrimmedConstraintsTree.Node[]>> stack = new LinkedList<>();
        Node curr = root;
        Node prev = null;
        TrimmedConstraintsTree.Node done = null;

        while (curr != null) {
            // moving down
            if (prev == null || prev == curr.parent) {
                // moving further down
                if (curr.data != null && curr.data.getClass().getName().equals(DecisionData.class.getName())) {
                    DecisionData d = (DecisionData) curr.data;
                    int cCount = d.children.length;
                    assert cCount > 0;

                    int idx = 0;
                    TrimmedConstraintsTree.Node[] arr = new TrimmedConstraintsTree.Node[cCount];
                    for (int i = 0; i < cCount; i++) {
                        if (d.children[i] != null) {
                            break;
                        }
                        arr[idx++] = null;
                    }

                    // moving back up
                    if (idx == cCount) {
                        done = generateTrimmedNode(d, arr);
                        Node tmp = curr;
                        curr = prev;
                        prev = tmp;
                        continue;
                    }

                    // moving further down
                    Pair<Integer, TrimmedConstraintsTree.Node[]> p = new Pair<>(idx, arr);
                    stack.push(p);
                    prev = curr;
                    curr = d.children[idx];
                    continue;
                }

                // moving back up
                if (curr.data == null || curr.data.getClass().getName().equals(UnsatisfiableData.class.getName())) {
                    done = null;
                } else if (curr.data.getClass().getName().equals(ResultData.class.getName())) {
                    ResultData d = (ResultData) curr.data;
                    done = new TrimmedConstraintsTree.ResultNode(d.result);
                } else if (curr.data.getClass().getName().equals(DontKnowData.class.getName())) {
                    done = TrimmedConstraintsTree.DONT_KNOW_NODE;
                }
                Node tmp = curr;
                curr = prev;
                prev = tmp;
            }
            // moving up
            else {
                Pair<Integer, TrimmedConstraintsTree.Node[]> p = stack.pop();
                DecisionData data = (DecisionData) curr.data;
                p._2[p._1] = done;

                int idx = p._1;
                while (++idx < p._2.length) {
                    if (data.children[idx] != null) {
                        break;
                    }
                }

                // moving further up
                if (idx == p._2.length) {
                    done = generateTrimmedNode(data, p._2);
                    prev = curr;
                    curr = curr.parent;
                    continue;
                }
                // moving down again
                p = new Pair(idx, p._2);
                stack.push(p);
                prev = curr;
                curr = data.children[idx];
            }
        }
        return done;
    }

    private static String traceToString(int[] decisionTrace) {
        if (decisionTrace == null) {
            return null;
        }

        StringBuilder builder = new StringBuilder();

        for (int x : decisionTrace) {
            builder.append(Character.forDigit(x, 10));
        }
        return builder.toString();
    }


    private TrimmedConstraintsTree.Node generateTrimmedNode(DecisionData d, TrimmedConstraintsTree.Node[] arr) {
        List<TrimmedConstraintsTree.Node> tchildren = new ArrayList<>();
        List<Expression<Boolean>> tconstraints = new ArrayList<>();
        boolean allDontKnow = true;
        for (int i = 0; i < arr.length; i++) {
            TrimmedConstraintsTree.Node tc = arr[i];
            if (tc == null)
                continue;
            tchildren.add(tc);
            tconstraints.add(d.constraints[i]);
            if (tc != TrimmedConstraintsTree.DONT_KNOW_NODE)
                allDontKnow = false;
        }

        if (tchildren.isEmpty())
            return null;
        if (tchildren.size() == 1)
            return tchildren.iterator().next();
        if (allDontKnow)
            return TrimmedConstraintsTree.DONT_KNOW_NODE;
        return new TrimmedConstraintsTree.InnerNode(tchildren, tconstraints);
    }


    public boolean isExplore() {
        return explore;
    }
}
