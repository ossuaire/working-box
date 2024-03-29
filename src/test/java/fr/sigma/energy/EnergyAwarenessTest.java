package fr.sigma.energy;

import java.util.TreeMap;
import java.util.ArrayList;

import org.apache.commons.lang3.tuple.Triple;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;



public class EnergyAwarenessTest {

    @Test
    public void initialize () {
        var ea = new EnergyAwareness("meow", 10, 4);
        assertEquals(0, ea.getFuncToIntervals().size());
        assertEquals(0, ea.getLocalEnergyData().size());
        assertEquals("meow", ea.getName());
    }


    
    @Test
    public void testSimple_combination () {
        var ea = new EnergyAwareness("meow", 10, 4);
        TreeRangeSet<Double> r1 = TreeRangeSet.create();
        TreeRangeSet<Double> r2 = TreeRangeSet.create();
        assert(ea._combination(r1,r2).isEmpty());
        r1.add(Range.closed(1.,2.));
        assert(ea._combination(r1,r2).enclosesAll(r1));
        assert(r1.enclosesAll(ea._combination(r1,r2)));
        assert(ea._combination(r2,r1).enclosesAll(r1));
        assert(r1.enclosesAll(ea._combination(r2,r1)));
    }

    @Test
    public void test_combination () {
        var ea = new EnergyAwareness("meow", 10, 4);
        TreeRangeSet<Double> r1 = TreeRangeSet.create();
        TreeRangeSet<Double> r2 = TreeRangeSet.create();
        r1.add(Range.closed(1.,2.));
        r2.add(Range.closed(3.,10.));
        TreeRangeSet<Double> expected = TreeRangeSet.create();
        expected.add(Range.closed(4., 12.));
        var result = ea._combination(r1, r2);
        assert(result.enclosesAll(expected));
        assert(expected.enclosesAll(result));
    }

    @Test
    public void test_combination_with_factorize () {
        var ea = new EnergyAwareness("meow", 10, 4);
        TreeRangeSet<Double> r1 = TreeRangeSet.create();
        TreeRangeSet<Double> r2 = TreeRangeSet.create();
        r1.add(Range.closed(1.,2.));
        r1.add(Range.closed(5.,6.));
        r2.add(Range.closed(3.,10.));
        TreeRangeSet<Double> expected = TreeRangeSet.create();
        expected.add(Range.closed(4., 16.));
        var result = ea._combination(r1, r2);
        assert(result.enclosesAll(expected));
        assert(expected.enclosesAll(result));
    }

    @Test
    public void test_combination_without_factorize () {
        var ea = new EnergyAwareness("meow", 10, 4);
        TreeRangeSet<Double> r1 = TreeRangeSet.create();
        TreeRangeSet<Double> r2 = TreeRangeSet.create();
        r1.add(Range.closed(1.,2.));
        r1.add(Range.closed(12.,13.));
        r2.add(Range.closed(3.,10.));
        TreeRangeSet<Double> expected = TreeRangeSet.create();
        expected.add(Range.closed(4., 12.));
        expected.add(Range.closed(15., 23.));
        var result = ea._combination(r1, r2);
        assert(result.enclosesAll(expected));
        assert(expected.enclosesAll(result));
    }


    
    @Test
    public void testCombineIntervalsOfNothing () {
        var ea = new EnergyAwareness("meow", 10, 4);
        assert(ea.getIntervals().isEmpty());
    }

    @Test
    public void testCombineIntervalsWithLocalAndRemoteIntervals () {
        var ea = new EnergyAwareness("meow", 10, 4);
        TreeRangeSet<Double> remoteCosts = TreeRangeSet.create();
        remoteCosts.add(Range.closed(13., 16.));
        ea.updateRemote("woof", remoteCosts);
        var toSend = ea.getIntervals();
        assert(remoteCosts.enclosesAll(toSend));
        
        var ea2 = new EnergyAwareness("anotherService", 10, 4);
        ea2.updateRemote("waf", remoteCosts);
        ea2.updateRemote("meow", toSend);
        TreeRangeSet<Double> expected = TreeRangeSet.create();
        expected.add(Range.closed(26., 32.));
        assert(expected.enclosesAll(ea2.getIntervals()));
        assert(ea2.getIntervals().enclosesAll(expected));        
    }



    @Test
    public void objectivesAllAlone () {
        var ea = new EnergyAwareness("meow", 10, 4);
        // doing with remote only because easier to manipulate
        // compared to kernel density calls        
        var remoteRange = new TreeMap<String, Range>();
        remoteRange.put("woof", Range.closed(13., 16.));
        var result = ea.getObjectivesFromInterval(14.,remoteRange);
        assertEquals(14., (double) result.get("woof"));
    }

    @Test
    public void objectivesMultipleServices () {
        var ea = new EnergyAwareness("meow", 10, 4);
        var remoteRange = new TreeMap<String, Range>();
        remoteRange.put("woof", Range.closed(13., 16.));
        remoteRange.put("waf", Range.closed(14., 16.));
        var result = ea.getObjectivesFromInterval(28., remoteRange);
        assertEquals(13.5, (double) result.get("woof"));
        assertEquals(14.5, (double) result.get("waf"));
    }

    @Test
    public void objectivesMultipleServicesWithSurplus () {
        var ea = new EnergyAwareness("meow", 10, 4);
        var remoteRange = new TreeMap<String, Range>();
        remoteRange.put("woof", Range.closed(12., 12.5));
        remoteRange.put("waf", Range.closed(14., 16.));
        var result = ea.getObjectivesFromInterval(28.,remoteRange);
        assertEquals(12.5, (double) result.get("woof"));
        assertEquals(15.5, (double) result.get("waf"));
    }



    @Test
    public void objectivesWithMissingOrNoDataGoesDefault () {
        var ea = new EnergyAwareness("meow", 10, 4);
        TreeRangeSet<Double> remoteRangeSet1 = TreeRangeSet.create();
        remoteRangeSet1.add(Range.closed(12., 12.5));
        ea.updateRemote("woof", remoteRangeSet1);
        var objectives = ea.getObjectives(28, false);
        // miss local
        assertEquals(-1., (double) objectives.get("woof"));
        assertEquals(-1., (double) objectives.get("meow"));

        // all seems good
        ea.addEnergyData(new Double[0], 0.);
        objectives = ea.getObjectives(28, false);
        assertEquals(0., (double) objectives.get("meow"));
        assertEquals(12.5, (double) objectives.get("woof"));
        
        // miss new remote
        ea.updateRemote("waf", TreeRangeSet.create());
        objectives = ea.getObjectives(28, false);
        assertEquals(0., (double) objectives.get("meow"));
        assertEquals(12.5, (double) objectives.get("woof"));
        assertEquals(-1., (double) objectives.get("waf"));        
    }
    
    @Test
    public void objectivesWithRangeSetWithOneRangeAndSurplus () {
        var ea = new EnergyAwareness("meow", 10, 4);
        ea.addEnergyData(new Double[0], 0.);
        
        TreeRangeSet<Double> remoteRangeSet1 = TreeRangeSet.create();
        remoteRangeSet1.add(Range.closed(12., 12.5));
        ea.updateRemote("woof", remoteRangeSet1);
        TreeRangeSet<Double> remoteRangeSet2 = TreeRangeSet.create();
        remoteRangeSet2.add(Range.closed(14., 16.));
        ea.updateRemote("waf", remoteRangeSet2);
        
        var objectives = ea.getObjectives(28, false);
        assertEquals(12.5, (double) objectives.get("woof"));
        assertEquals(15.5, (double) objectives.get("waf"));
    }

    @Test
    public void objectiveWithRangeSetMultipleChoices () {
        var ea = new EnergyAwareness("meow", 10, 4);
        ea.addEnergyData(new Double[0], 0.);

        TreeRangeSet<Double> remoteRangeSet1 = TreeRangeSet.create();
        remoteRangeSet1.add(Range.closed(10., 20.));
        remoteRangeSet1.add(Range.closed(25., 40.));
        ea.updateRemote("woof", remoteRangeSet1);
        TreeRangeSet<Double> remoteRangeSet2 = TreeRangeSet.create();
        remoteRangeSet2.add(Range.closed(40., 60.));
        remoteRangeSet2.add(Range.closed(80., 110.));       
        ea.updateRemote("waf", remoteRangeSet2);
        
        var objectives = ea.getObjectives(100., false);
        assertEquals(15, (double) objectives.get("woof")); // 10+5
        assertEquals(85, (double) objectives.get("waf")); // 80+5
        var objectives2 = ea.getObjectives(85., false);
        assertEquals(35, (double) objectives2.get("woof")); // 25+10
        assertEquals(50, (double) objectives2.get("waf")); // 40+10
    }

    @Test
    public void objectiveWithoutSatisfyingSolution () {
	// i.e. every service cannot run with its minimal requirement
	var ea = new EnergyAwareness("meow", 10, 4);
	ea.addEnergyData(new Double[0], 10.);

	TreeRangeSet<Double> remoteRangeSet1 = TreeRangeSet.create();
        remoteRangeSet1.add(Range.closed(10., 20.));
        remoteRangeSet1.add(Range.closed(25., 40.));
        ea.updateRemote("woof", remoteRangeSet1);
        TreeRangeSet<Double> remoteRangeSet2 = TreeRangeSet.create();
        remoteRangeSet2.add(Range.closed(40., 60.));
        remoteRangeSet2.add(Range.closed(80., 110.));       
        ea.updateRemote("waf", remoteRangeSet2);
        
	var objectives = ea.getObjectives(5., false); // no service can run
	assertEquals(-1, (double) objectives.get("woof"));
	assertEquals(-1, (double) objectives.get("waf"));
	assertEquals(-1, (double) objectives.get("meow"));

	var objectives2 = ea.getObjectives(11., false); // second service can't run
	assertEquals(-1, (double) objectives.get("woof"));
	assertEquals(-1, (double) objectives.get("waf"));
	assertEquals(-1, (double) objectives.get("meow"));	
    }



    @Test
    public void testWithoutFullData () {
	var ea = new EnergyAwareness("meow", 10, 4);
	ea.addEnergyData(new Double[0], 10.);
	ArrayList<String> remotes = new ArrayList();
	remotes.add("Simon");
	remotes.add("Thomas");
	ea.updateRemotes(remotes);
	TreeRangeSet<Double> remoteRangeSet2 = TreeRangeSet.create();
        remoteRangeSet2.add(Range.closed(40., 60.));
        remoteRangeSet2.add(Range.closed(80., 110.));

	ea.updateRemote("Thomas", remoteRangeSet2);
	Double[] args = {10., 0.};
	Triple<TreeMap<String, Double>, Double[], Boolean> os = ea.newFunctionCall(1000, args);
	os = ea.newFunctionCall(1000, args);
	os = ea.newFunctionCall(1000, args);
	os = ea.newFunctionCall(1000, args);
	os = ea.newFunctionCall(1000, args);
	var objectives = os.getLeft();
	var solution = os.getMiddle();

	assertEquals(3, objectives.size());
	assertEquals(-1., (double) objectives.get("Simon"));
	assertEquals(10., (double) objectives.get("meow"));
	assertEquals(110., (double) objectives.get("Thomas"));
    }
				       
    
    @Test
    public void testSimpleCallToFunc () {
        // |local data| = 10, |filter threshold| = 4
        var ea = new EnergyAwareness("meow", 10, 4);
        Double[] args = {10., 0.};
        var os = ea.newFunctionCall(1000, args);	
        // (TODO) real workflow
    }



    @Test
    public void testFairnessBetweenTwoSolutionsLarge () {
        var ea = new EnergyAwareness("meow", 10, 4, 1, 0.5, 15);
        var eaNoFair = new EnergyAwareness("meow", 10, 4, 1, 0., 15);

	ea.addEnergyData(new Double[0], 10.);
       	eaNoFair.addEnergyData(new Double[0], 10.);
        
        TreeRangeSet<Double> remoteRangeSet1 = TreeRangeSet.create();
        remoteRangeSet1.add(Range.closed(1., 1.));
        remoteRangeSet1.add(Range.closed(4., 4.));
        ea.updateRemote("woof", remoteRangeSet1);
        eaNoFair.updateRemote("woof", remoteRangeSet1);
        TreeRangeSet<Double> remoteRangeSet2 = TreeRangeSet.create();
        remoteRangeSet2.add(Range.closed(4., 4.));
        remoteRangeSet2.add(Range.closed(8., 8.));
        ea.updateRemote("waf", remoteRangeSet2);
        eaNoFair.updateRemote("waf", remoteRangeSet2);
        
        var objectives = eaNoFair.getObjectives(9., true);
        assertEquals(1., (double) objectives.get("woof")); 
        assertEquals(8., (double) objectives.get("waf")); 

        objectives = eaNoFair.getObjectives(8., true);
        assertEquals(4., (double) objectives.get("woof")); 
        assertEquals(4., (double) objectives.get("waf")); 

        var objectivesFair = ea.getObjectives(9., true);
        assertEquals(4., (double) objectivesFair.get("woof")); 
        assertEquals(4., (double) objectivesFair.get("waf")); 
    }

    
}
