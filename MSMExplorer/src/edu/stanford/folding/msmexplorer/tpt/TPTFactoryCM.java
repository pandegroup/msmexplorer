/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.stanford.folding.msmexplorer.tpt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

import prefuse.data.Graph;
import prefuse.data.Edge;
import prefuse.data.Node;
import prefuse.data.Tuple;
import prefuse.data.tuple.TupleSet;
import prefuse.data.column.Column;

import org.apache.commons.math.linear.RealMatrix;
import org.apache.commons.math.linear.OpenMapRealMatrix;
import org.apache.commons.math.linear.ArrayRealVector;
import org.apache.commons.math.linear.RealVector;
import org.apache.commons.math.linear.DecompositionSolver;
import org.apache.commons.math.linear.LUDecompositionImpl;


/**
 * Used to perform TPT calculations on a graph.
 *
 * @author gestalt
 */
public class TPTFactoryCM {

    private static final int kSTROKE_INCREMENT = 2;

    private final Graph m_graph;               //graph
    private final TupleSet m_source;           //source node(s)
    private final TupleSet m_target;           //target node(s)
    private final OpenMapRealMatrix m_tProb;
    // private static TupleSet m_intermediate;     //intermediate nodes
    //private final int kNumIStates;              //number intermediate states
    private final int kNumKStates;              //number target states;
    private final int kMatSize;
    private OpenMapRealMatrix m_fFluxes;
    private OpenMapRealMatrix m_old_fFluxes;

    /**
     * Constructor for TPTFacotry. Sets graph to use
     * for this TPTFactory instance as well as source
     * and target nodes/groups thereof.
     *
     * @param g, Graph to analyze
     * @param source, Group of source Nodes
     * @param target, Group of Target Nodes
     */
    public TPTFactoryCM(Graph g, TupleSet source, TupleSet target) {

        this.m_graph = g;
        this.m_source = source;
        this.m_target = target;
        this.kMatSize = m_graph.getNodeCount();

	System.out.println("Factory established");
        this.m_tProb = transitionMatrix();
	System.out.println("tProb reconstructed");


        this.kNumKStates = target.getTupleCount();

        double[] forwardCommittors = getForwardCommittors();
	System.out.println("q+ obtained");
        double[] backwardCommittors = getBackwardCommittors(forwardCommittors);
	System.out.println("q- obtained");
        double[] eqProbs = getEqProbs();
	eqProbs = normalize(eqProbs);
	System.out.println("eqProbs reconstructed");
        
        this.m_old_fFluxes = getFluxes(forwardCommittors, backwardCommittors, eqProbs);
	System.out.println("fluxes obtained");
        this.m_fFluxes = deepCopy(m_old_fFluxes);
	System.out.println("fluxes copied");

    }

    
    public ArrayList<Edge> getNextEdge() {

//        return GetHighFluxPath();
      return getHighFluxPathV1();
    }


    public void reset() {
        this.m_fFluxes = deepCopy(m_old_fFluxes);
        
    }

    private double[] normalize(double[] arr) {

	    double sum = 0.0;

	    for (int i = 0; i < arr.length; ++i)
		    sum += arr[i];

	    for (int i = 0; i < arr.length; ++i)
		    arr[i] /= sum;

	    return arr;
    }

    /**
     * Simply returns a TupleSet containing only the
     * intermediate states.
     *
     * Obsolete.
     *
     * @return
     */
    private TupleSet getIntermediateStates() {

        TupleSet inters = m_graph.getNodes();
        Iterator sItr = m_source.tuples();
        Iterator tItr = m_target.tuples();

        for (Tuple t = (Tuple) sItr.next(); sItr.hasNext(); t = (Tuple) sItr.next()) {
            inters.removeTuple(t);
        }

        for (Tuple t = (Tuple) tItr.next(); tItr.hasNext(); t = (Tuple) tItr.next()) {
            inters.removeTuple(t);
        }

        return inters;
    }

    /**
     * Creates a transitionMatrix from graph backing data.
     * This should match the .dat file from which the graphml
     * file was created (or equivalently, the corresponding
     * tProb.dat for whatever input data was provided)
     */
    private OpenMapRealMatrix transitionMatrix() {

//        double[][] tProb = new double[kMatSize][kMatSize];
	OpenMapRealMatrix tProb = new OpenMapRealMatrix(kMatSize, kMatSize);

        for (int i = 0; i < m_graph.getEdgeCount(); ++i) {
            Edge e = m_graph.getEdge(i);
            int source = e.getSourceNode().getRow();
            int target = e.getTargetNode().getRow();

             Double prob = e.getDouble("probability");
	     if (prob != 0.0)
		     tProb.setEntry(source, target, prob);
        }

        return tProb;
    }

    /**
     * Calculates forward committors and returns them in an array. Index into
     * array corresponds to graph Node id and tProb row (ith state, standardly)
     */
    private double[] getForwardCommittors() {

	System.out.println("fc start");
        ArrayList<Integer> source = getIndicies(m_source);
        ArrayList<Integer> target = getIndicies(m_target);

        //Copy of tProb in RealMatrix form
        OpenMapRealMatrix tProbRM = new OpenMapRealMatrix(m_tProb.copy());

        RealVector aug = new ArrayRealVector(kMatSize); //Holds "augmented" col
	System.out.println("init done");

	for (int i = 0; i < kMatSize; ++i)
		tProbRM.addToEntry(i, i, -1);
//        tProbRM.subtract(MatrixUtils.createRealIdentityMatrix(kMatSize));

	System.out.println("enter loop");
        for (int i = 0; i < kMatSize; ++i) {

            if ( target.contains(new Integer(i)) ) {
                tProbRM.setColumn(i, new double[kMatSize]);
                tProbRM.setRow(i, new double[kMatSize]);
                tProbRM.setEntry(i, i, 1);
                aug.setEntry(i, 1);

            } else if ( source.contains(new Integer(i)) ) {
                tProbRM.setColumn(i, new double[kMatSize]);
                tProbRM.setRow(i, new double[kMatSize]);
                tProbRM.setEntry(i, i, 1); //Note that aug[i] is already 0.0

            } else {
                aug.setEntry(i, -sumOverTarget(i));
            }
        }

	System.out.println("to make solver");

//        System.out.println(Arrays.deepToString(tProbRM.getData()));
//        System.out.println(Arrays.toString(aug.getData()));

        DecompositionSolver solver = new LUDecompositionImpl(tProbRM).getSolver();

	System.out.println("to solve");
        return (solver.solve(aug)).getData();
    }


    private double[] getBackwardCommittors(double[] fCommittors) {

        double[] bCommittors = new double[kMatSize];

        for ( int i = 0; i < kMatSize; ++i )
            bCommittors[i] = 1.0d - fCommittors[i];

        return bCommittors;
    }


    private ArrayList<Integer> getIndicies(TupleSet ts) {
        
        
        ArrayList<Integer> indicies = new ArrayList<Integer>();

        Iterator tuples = ts.tuples();
        while (tuples.hasNext()) 
            indicies.add(new Integer(((Tuple)tuples.next()).getRow()));
        

        return indicies;
    }
    

    private double sumOverTarget(int index) {

        ArrayList<Integer> indicies = getIndicies(m_target);

        double sum = 0;

        for (Integer i : indicies)
            sum += m_tProb.getEntry(index, i);

        return sum;
    }


    private double[] getEqProbs() {

        Column source = m_graph.getNodeTable().getColumn("eqProb");
        
        double[] eqProb = new double[kMatSize];

        for ( int i = 0; i < kMatSize; ++i ) 
            eqProb[i] = source.getDouble(i);

        return eqProb;
    }


    private OpenMapRealMatrix getFluxes( double[] fCommittors, double[] bCommittors, double[] eqProbs ) {

        OpenMapRealMatrix fFluxes = new OpenMapRealMatrix(kMatSize,kMatSize);

        for ( int i = 0; i < kMatSize; ++i)
            for (int j = 0; j < kMatSize; ++j)
                if ( m_tProb.getEntry(i,j) != 0.0d && i != j )
                    fFluxes.setEntry(i, j, eqProbs[i]*bCommittors[i]*m_tProb.getEntry(i,j)*fCommittors[j]);

        for ( int i = 0; i < kMatSize; ++i )
            for( int j = 0; j < kMatSize; ++j ) {
                    double netFlux = fFluxes.getEntry(i, j) - fFluxes.getEntry(j, i);
		    if (netFlux < 0.0)
			    fFluxes.setEntry(i,j,0.0d);
		    else 
			    fFluxes.setEntry(i, j, netFlux);
		}

        return fFluxes;
    }


    private ArrayList<Edge> GetHighFluxPath() {

        OpenMapRealMatrix fluxes = new OpenMapRealMatrix(m_fFluxes);

        ArrayList<Integer> indicies = getIndicies(m_source);

        ArrayList<Integer> iList = new ArrayList<Integer>();
        ArrayList<Double> fluxList = new ArrayList<Double>();

        int index = getIndicies(m_target).get(0).intValue();

        iList.add(index);

       // int itr = 0;
        final int maxItr = m_fFluxes.getRowDimension();
        do {

            double[] arr = fluxes.getColumnVector(index).getData();
            index = argmax(arr);

            int numTimes = 0;
            while (iList.contains(index)) {
                arr[index] = -1.0d;
                index = argmax(arr);
                if (++numTimes >= maxItr)
                    return new ArrayList();
            }

            iList.add(index);
            fluxList.add(arr[index]);
            //++itr;

            //if ( itr > maxItr )
              //  return new ArrayList();

        } while ( (!(indicies.contains(index))) );

        double f = fluxList.get(argmin(fluxList)).doubleValue();
	System.out.println(f);

        ArrayList<Edge> edgeList = new ArrayList<Edge>();

	 for ( int k = iList.size() - 1; k < 0; --k ) {

            int i = iList.get(k);
            int j = iList.get(k-1);

            this.m_fFluxes.addToEntry(i, j, -f);

            Node source = m_graph.getNode(i);
            Node target = m_graph.getNode(j);
            Edge e = m_graph.getEdge(source, target);

            source.setDouble("flux", source.getDouble("flux") + f);
            target.setDouble("flux", target.getDouble("flux") + f);
            e.setDouble("flux", e.getDouble("flux") + f);

            edgeList.add(e);
        }

        return edgeList;
    }

    private ArrayList<Edge> getHighFluxPathV1() {
        OpenMapRealMatrix fluxes = new OpenMapRealMatrix(this.m_fFluxes.copy());

        ArrayList<Integer> indicies = getIndicies(m_target);

        ArrayList<Integer> iList = new ArrayList<Integer>();
        ArrayList<Double> fluxList = new ArrayList<Double>();

        int index = getIndicies(m_source).get(0).intValue();
        double[] arr = fluxes.getRowVector(index).getData().clone();

        boolean hasPath = decompose( index, iList, fluxList, indicies, fluxes );
        if ( hasPath == false )
            return new ArrayList();

        iList.add(index);

        double f = fluxList.get(argmin(fluxList)).doubleValue();
	System.out.print(f);

        ArrayList<Edge> edgeList = new ArrayList<Edge>();

        for ( int k = 0; k < iList.size()-1; ++k ) {

            int j = iList.get(k);
            int i = iList.get(k+1);

            this.m_fFluxes.addToEntry(i, j, -f);

            Node source = m_graph.getNode(i);
            Node target = m_graph.getNode(j);
            Edge e = m_graph.getEdge(source, target);

            source.setDouble("flux", source.getDouble("flux") + f);
            target.setDouble("flux", target.getDouble("flux") + f);
            e.setDouble("flux", e.getDouble("flux") + f);
            
            edgeList.add(e);
        }

        return edgeList;
    }

    final private boolean decompose( int index, ArrayList<Integer> iList,
            ArrayList<Double> fList, ArrayList<Integer> target, RealMatrix fluxes ) {

            if ( target.contains(index) )
                return true;

            double[] arr = fluxes.getRowVector(index).getData().clone();

            while (hasNonZero(arr)) {

                index = argmax(arr);

                if (decompose(index, iList, fList, target, fluxes)) {
                   iList.add(index);
                   fList.add(arr[index]);
                   return true;

                } else {
                    arr[index] = 0.0d;
                }
            }

            return false;
    }

    private boolean hasNonZero( double[] arr ) {

        int length = arr.length;

        for ( int i = 0; i < length; ++i ) {
            if (arr[i] > 0.0d)
                return true;
        }
        return false;
    }


    private int argmax( double[] arr ) {
        int index = 0;
        double max = arr[0];

        for (int i = 1; i < arr.length; ++i)
            if ( arr[i] > max ) {
                index = i;
                max = arr[i];
            }
        
        return index;
    }

    
    private int argmin( double[] arr ) {
        int index = 0;
        double min = arr[0];

        for (int i = 1; i < arr.length; ++i)
            if ( arr[i] < min ) {
                index = i;
                min = arr[i];
            }
        
        return index;
    }


     private int argmin( ArrayList<Double> arr ) {

        Object[] bigD = arr.toArray();
        double[] smallD = new double[bigD.length];

        for ( int i = 0; i < bigD.length; ++i )
            smallD[i] = ((Double)bigD[i]).doubleValue();
        
        return argmin( smallD );
    }

    public static double[][] deepCopy( double[][] source ) {
        int length = source.length;

        double[][] target = new double[length][source[0].length];

        for ( int i = 0; i < length; ++i ) 
            target[i] = source[i].clone();

        return target;
    }

   public static OpenMapRealMatrix deepCopy( OpenMapRealMatrix source) {

	OpenMapRealMatrix target = new OpenMapRealMatrix(source.copy());

        return target;
    }
     
}