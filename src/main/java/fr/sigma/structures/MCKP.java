package fr.sigma.structures;

import java.util.ArrayList;
import java.util.stream.Collectors;



/**
 * Class that solves and caches the multiple-choice knapsack problem
 * where exactly one element of in each set of elements must be
 * chosen the closest of the targeted objective.
 */
public class MCKP {

    private int maxObjective;
    private ArrayList<MCKPElement> elements;

    private double ratio; // upscale or downscale
    private ArrayList<ArrayList<Integer>> m; // matrix of results
    private ArrayList<Integer> iGroup; // indexes of row with group change

    
    public MCKP (int maxObjective, ArrayList<MCKPElement> elements) {
        // (TODO) /!\ check that elements are sorted by group and by
        // ascending weight/profit
        this.maxObjective = maxObjective;
        this.elements = elements;
        this.elements.add(0, MCKPElement.PLACEHOLDER()); // convenience
        m = new ArrayList();
        iGroup = new ArrayList();
    }

    public void addElement (MCKPElement element) {
        // (TODO) /!\ must reprocess things after the insertion in the
        // matrix. /!\ might be necessary to reprocess all if the objective
        // * current_ratio is lower than this element.
    }

    public int getMaxObjective () {
        return maxObjective;
    }

    public ArrayList<ArrayList<Integer>> getMatrix () {
        return m;
    }

    public ArrayList<Integer> getIGroup () {
        return iGroup;
    }

    public ArrayList<MCKPElement> getElements () {
        return elements;
    }
        


    public ArrayList<MCKPElement> solve(double objective) {        
        // (TODO) normalize objective with intervals
        // (TODO) if objective > maxObjective, process missing data
        if (m.isEmpty())
            process();
        var listOfIndices = backtrack((int) objective);
        return listOfIndices.stream().map(i->elements.get(i))
            .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Fill the matrix of size ~maxObjective*nbElements. 
     */    
    public void process() {
        int iPreviousGroup = -1;
        int previousGroup = elements.get(0).group;
        int currentGroup = elements.get(0).group;
        
        for (int i = 0; i < elements.size(); ++i) {
            m.add(new ArrayList());
            m.get(i).add(-1); // first column full of 0
        }

        m.get(0).set(0, 0);
        for (int w = 0; w < maxObjective; ++w)
            m.get(0).add(0); // first row full of 0
        
        int minWeight = 0; // remove invalid possibility
        
        // process the matrix
        for (int i = 1; i < elements.size(); ++i) {
            var e = elements.get(i);
            
            boolean newGroup = e.group != previousGroup;            
            if (newGroup) {
                minWeight += e.weight;
                previousGroup = e.group;
                iPreviousGroup = i-1;
                iGroup.add(iPreviousGroup);
            }
            
            for (int w = 1; w <= maxObjective; ++w) {
                if (w < minWeight) 
                    m.get(i).add(-1);
                else  {
                    int diag = (w < e.weight) ?
                        -1 : m.get(iPreviousGroup).get(w - e.weight);
                    int option1 = (diag < 0) ?
                        -1 : diag + e.profit ; // diag
                    int option2 = newGroup ?
                        -1 : m.get(i-1).get(w); // above
                    m.get(i).add(Math.max(option1, option2));
                }
            }
        }    
    }

    /**
     * Uses the matrix of intermediate results to retrieves the best
     * value of each set that fit the objective.
     * @param objective the objective
     * @return a list of indices that correspond to the chosen items
     * in the list of elements of this solver.
     */
    public ArrayList<Integer> backtrack(int objective) {
        if (elements.size()<=1)
            return new ArrayList(); // default empty
       
        // start at the proper row column in the matrix
        int y = m.size() - 1;
        int x = objective;

	if (m.get(y).get(x) == -1)
	    return new ArrayList(); // no solution

	var indexOfValidItems = new ArrayList<Integer>();
        int iCurrentGroup = iGroup.size();

        while (y > 0) {
            while (x > 0 &&
                   m.get(y).get(x).equals(m.get(y).get(x-1)))
                --x; // go left
            while (y > 0 &&
                   m.get(y).get(x).equals(m.get(y-1).get(x)) &&
                   y > iGroup.get(iCurrentGroup - 1))
                --y; // go up
            if (y == iGroup.get(iCurrentGroup - 1)) {
                ++y; // went one to many
            }

            indexOfValidItems.add(y);
            x -= elements.get(y).weight;
            y = iGroup.get(iCurrentGroup - 1);
            --iCurrentGroup;
        }

        return indexOfValidItems;        
    }
    
    
}
    


