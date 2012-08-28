/*
 * Copyright (C) 2012 Stanford University
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package edu.stanford.folding.msmexplorer.util;

import prefuse.action.layout.graph.ForceDirectedLayout;
import prefuse.util.force.ForceSimulator;
import prefuse.visual.EdgeItem;

/**
 *
 * @author gestalt
 */
public class AggForceDirectedLayout extends ForceDirectedLayout {

    /**
     * Create a new AggForceDirectedLayout. By default, this layout will not
     * restrict the layout to the layout bounds and will assume it is being
     * run in animated (rather than run-once) fashion.
     * @param graph the data group to layout. Must resolve to a Graph instance.
     */
    public AggForceDirectedLayout(String graph)
    {
        super(graph, false, false);
    }

    /**
     * Create a new AggForceDirectedLayout.
     * @param group the data group to layout. Must resolve to a Graph instance.
     * @param enforceBounds indicates whether or not the layout should require
     * that all node placements stay within the layout bounds.
     * @param runonce indicates if the layout will be run in a run-once or
     * animated fashion. In run-once mode, the layout will run for a set number
     * of iterations when invoked. In animation mode, only one iteration of the
     * layout is computed.
     */
    public AggForceDirectedLayout(String group,
            boolean enforceBounds, boolean runonce)
    {
	    super(group, enforceBounds, runonce);
    }

    @Override
    protected float getSpringCoefficient(EdgeItem e) {
	    return multiplyIfSameMapping(e, 0, 2.f);
    }

    @Override
    protected float getSpringLength(EdgeItem e) {
	    return multiplyIfSameMapping(e, 1, 0.4f);
    }

    private float multiplyIfSameMapping(EdgeItem e, int param, float multiplier) {
	    ForceSimulator fsim = this.getForceSimulator();
	    float val = fsim.getForces()[2].getParameter(param);
	    if (e.getSourceNode().getColumnIndex("mapping") >= 0) {
		    int sourceMap = e.getSourceNode().getInt("mapping");
		    int targetMap = e.getTargetNode().getInt("mapping");
		    if (sourceMap == targetMap) {
			    return multiplier*val;
		    }
	    }
	    return val;
    }
	
}
