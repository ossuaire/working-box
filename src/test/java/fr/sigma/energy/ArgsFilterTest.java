package fr.sigma.energy;

import java.util.ArrayList;
import java.util.Arrays;
    
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;



public class ArgsFilterTest {

    @Test
    public void initialize () {
        var filter = new ArgsFilter();
        // /!\ everything is implementation dependent here...
        assertEquals(14, filter.getThreshold());
    }

    @Test
    public void insertWithoutArgs () {
        var filter = new ArgsFilter();
        Double[] args = {};
        var stop = filter.isTriedEnough(args);
        assert(!stop);
    }
    
    @Test
    public void firstInsert () {
        var filter = new ArgsFilter();
        Double[] args = {42.};
        var stop = filter.isTriedEnough(args);
        assert(!stop);
    }

    @Test
    public void aboveThresh () {
        var filter = new ArgsFilter(4);
        Double[] args = {42.};
        var stop = filter.isTriedEnough(args);
        assert(!stop);
        filter.tryArgs(args);      
        stop = filter.isTriedEnough(args);
        assert(!stop);
        filter.tryArgs(args);
        stop = filter.isTriedEnough(args);
        assert(!stop);
        filter.tryArgs(args);
        stop = filter.isTriedEnough(args);
        assert(!stop);
        filter.tryArgs(args);
        stop = filter.isTriedEnough(args);
        assert(stop);
    }
    
}
