package fr.sigma.energy;

import fr.sigma.structures.Pair;
import fr.sigma.structures.MCKP;
import fr.sigma.structures.MCKPElement;

import java.util.Objects;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Iterator;

import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
    


/**
 * Provides data structures and algorithms to propagate energy
 * awareness as part of the workflow; upgrade or downgrade arguments
 * to adapt to energy consumption objective.
 */
public class EnergyAwareness {

    private Logger logger = LoggerFactory.getLogger(getClass());
    
    private TreeMap<String, TreeRangeSet<Double>> funcToIntervals;
    private LocalEnergyData localEnergyData;
    private ArgsFilter argsFilter;
    
    private final String name;
    
    public EnergyAwareness(String name, int maxSizeOfLocalData, int thresholdFilter) {
        funcToIntervals = new TreeMap();
        localEnergyData = new LocalEnergyData(maxSizeOfLocalData);
        argsFilter = new ArgsFilter(thresholdFilter);
        this.name = name;
    }

    public TreeMap<String, TreeRangeSet<Double>> getFuncToIntervals() {
        return funcToIntervals;
    }

    public LocalEnergyData getLocalEnergyData() {
        return localEnergyData;
    }

    public String getName() {
        return name;
    }



    /**
     * Process the new call to the function.
     * @param objective the objective that has been set by parent service.
     * @param args the args that matter to the local function
     * @return a pair <objectives , self-tuned args>
     */
    public Pair<TreeMap<String, Double>, Double[]> newFunctionCall(int objective,
                                                                   Double[] args) {
        if (objective < 0) {
            logger.info("This box has no energy objective defined.");
            return new Pair(getObjectives(objective), args); // default
        }
        
        logger.info(String.format("This box has an energy consumption objective of %s.",
                                  objective));
        
        TreeMap<String, Double> objectives = null;
        Double[] solution = null;
        if (argsFilter.isTriedEnough(args)) {
            objectives = getObjectives(objective);
            logger.info(String.format("Distributes energy objective as: %s.", objectives));
            
            solution = solveObjective(objectives.get(name));
            if (!Objects.isNull(solution)) {
                logger.info(String.format("Rewrites local arguments: %s -> %s.",
                                          Arrays.toString(args),
                                          Arrays.toString(solution)));
            }
        }
        return new Pair(objectives, solution);
    }
    

    
    public void addEnergyData(Double[] args, double cost) {
        localEnergyData.addEnergyData(args, cost);
    }

    public void updateRemotes(ArrayList<String> names) {
        for (var func : names)
            funcToIntervals.put(func, TreeRangeSet.create());
    }

    public void updateRemote(String func, TreeRangeSet<Double> costs) {
        // (TODO) could be important to handle version of data
        funcToIntervals.put(func, costs);
    }
    
    /**
     * Combine local intervals with ones got from remote services to
     * create a new interval. It should be sent to parent service.
     */
    public TreeRangeSet<Double> combineIntervals() {
        var result = localEnergyData.getIntervals();
        for (var interval : funcToIntervals.values())
            result = _combination(result, interval);        
        return result;
    }
    
    public TreeRangeSet<Double> getIntervals() { // alias of combine
        return combineIntervals();
    }
    
    public static TreeRangeSet<Double> _combination(RangeSet<Double> i1,
                                                    RangeSet<Double> i2) {
        TreeRangeSet<Double> result = TreeRangeSet.create();
        // #A quick defaults
        if (i1.isEmpty() && i2.isEmpty())
            return result; // empty
        if (i1.isEmpty()) {
            result.addAll(i2);
            return result;
        }
        if (i2.isEmpty()) {
            result.addAll(i1);
            return result;
        }

        // #B otherwise, all combinations       
        // /!\ may be expensive without range factorization, i.e., it
        // has a quadratic complexity
        for (Range<Double> r1 : i1.asRanges()) {
            for (Range<Double> r2 : i2.asRanges()) {
                result.add(Range.closed(r1.lowerEndpoint() + r2.lowerEndpoint(),
                                        r1.upperEndpoint() + r2.upperEndpoint()));
            }
        }   
        return result;
    }

    public TreeMap<String, Double> getObjectives(double objective) {
        // Check if has enough data to create objectives, otherwise, default
        // values are returned.
        boolean goDefault = localEnergyData.getIntervals().isEmpty();	
        for (var interval : funcToIntervals.values()) {
	    if (!goDefault && interval.isEmpty())
                goDefault = true;
	}	
	var defaultResult = new TreeMap<String, Double>();
	defaultResult.put(name, -1.);
	for (var func : funcToIntervals.keySet())
	    defaultResult.put(func, -1.);

        if (goDefault) 
            return defaultResult;

	// Otherwiiiiiiiiise, process objectives of children and self.
	
        double ratio = 1000. / objective; // (TODO) configurable scaling
        var groupToFunc = new TreeMap<Integer, String>();

        var mckpElements = new ArrayList<MCKPElement>();

        var localIntervals = localEnergyData.getIntervals();
        groupToFunc.put(0, name);
        int groupIndex = 0;
        for (Range<Double> interval : localIntervals.asRanges())
            mckpElements.add(new MCKPElement((int)(interval.lowerEndpoint()*ratio),
                                             (int)(interval.lowerEndpoint()*ratio),
                                             groupIndex));
        
        ++groupIndex;
        
        for (Map.Entry<String, TreeRangeSet<Double>> kv : funcToIntervals.entrySet()) {
            for (var intervals : kv.getValue().asRanges())
                mckpElements.add(new MCKPElement((int)(intervals.lowerEndpoint()*ratio),
                                                 (int)(intervals.lowerEndpoint()*ratio),
                                                 groupIndex));
            groupToFunc.put(groupIndex, kv.getKey());
            ++groupIndex;
        }

        var mckp = new MCKP(1000, mckpElements); // (TODO) cache mckp
        var solution = mckp.solve(1000);

	if (solution.isEmpty())
	    return defaultResult;
	
        var funcToInterval = new TreeMap<String, Range>();
        for (int i = 0; i < solution.size(); ++i) {            
            double value = solution.get(i).weight / ratio;
            String func = groupToFunc.get(solution.get(i).group);
            TreeRangeSet<Double> interval = (func.equals(name)) ?
                localIntervals : funcToIntervals.get(func);
                
            double distance = Double.MAX_VALUE;
            Range<Double> closestRange = null;
            for (Range<Double> range : interval.asRanges()) {
                if (distance > Math.abs(range.lowerEndpoint() - value)) {
                    distance = Math.abs(range.lowerEndpoint() - value);
                    closestRange = range;
                }
            }
            
            funcToInterval.put(func, closestRange);
        }
        
        return getObjectivesFromInterval(objective, funcToInterval);
    }

    /**
     * Gives minimal energy to everyone then distributes equally among 
     * services.
     */ 
    public static TreeMap<String, Double> getObjectivesFromInterval
        (double objective,
         TreeMap<String, Range> funcToInterval) {        
        var results = new TreeMap<String, Double>();
        
        // default value -1 for everyone.
        if (objective < 0) {
            for (var service  : funcToInterval.keySet())
                results.put(service, -1.);
            return results;
        }
        
        var objectiveToDistribute = objective;        
        var funcToRange = new ArrayList<Pair<String, Double>>();
        for (Map.Entry<String, Range> kv : funcToInterval.entrySet()) {
            Range<Double> span = kv.getValue();            
            funcToRange.add(new Pair(kv.getKey(),
                                     span.upperEndpoint() - span.lowerEndpoint()));
            objectiveToDistribute -= span.lowerEndpoint(); // o = o - min
        }

        // v (UGLY) Double -> double -> Double -> int
        funcToRange.sort((a, b)-> (new Double(a.second - b.second)).intValue());
        var nbShares = funcToRange.size();

        for (Pair<String, Double> kv : funcToRange) {
            var share = objectiveToDistribute/nbShares;
            var surplus = share - kv.second;
            var give = share;
            if (surplus > 0) {
                give = kv.second;
                objectiveToDistribute += surplus;
            }
            objectiveToDistribute -= share;
            nbShares -= 1;

            Range<Double> span = funcToInterval.get(kv.first);
            results.put(kv.first, give + span.lowerEndpoint());
        }
        
        return results;
    }


    public Double[] solveObjective(double objective) {
	if (objective < 0) return null; // default when objective unknown	
        return localEnergyData.getClosest(objective);
    }
    
}

