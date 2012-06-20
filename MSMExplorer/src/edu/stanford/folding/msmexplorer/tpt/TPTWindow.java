package edu.stanford.folding.msmexplorer.tpt;

import java.awt.Insets;
import java.util.ArrayList;
import java.util.Iterator;

import java.awt.Color;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import prefuse.Constants;
import prefuse.Display;
import prefuse.Visualization;
import prefuse.action.layout.graph.ForceDirectedLayout;
import prefuse.action.RepaintAction;
import prefuse.action.assignment.ColorAction;
import prefuse.action.assignment.DataSizeAction;
import prefuse.data.util.BreadthFirstIterator;
import prefuse.action.layout.AxisLayout;
import prefuse.controls.ZoomControl;
import prefuse.controls.DragControl;
import prefuse.controls.PanControl;
import prefuse.controls.WheelZoomControl;
import prefuse.controls.ZoomToFitControl;
import prefuse.controls.HoverActionControl;
import prefuse.data.Graph;
import prefuse.data.Edge;
import prefuse.data.expression.ColumnExpression;
import prefuse.data.expression.parser.ExpressionParser;
import prefuse.data.column.Column;
import prefuse.data.Tuple;
import prefuse.data.event.TupleSetListener;
import prefuse.data.tuple.TupleSet;
import prefuse.data.tuple.DefaultTupleSet;
import prefuse.action.ActionList;
import prefuse.render.ShapeRenderer;
import prefuse.render.EdgeRenderer;
import prefuse.render.DefaultRendererFactory;
import prefuse.render.LabelRenderer;
import prefuse.util.ColorLib;
import prefuse.util.force.Force;
import prefuse.util.force.ForceSimulator;
import prefuse.util.display.ExportDisplayAction;
import prefuse.util.ui.JForcePanel;
import prefuse.util.ui.JRangeSlider;
import prefuse.visual.VisualItem;
import edu.stanford.folding.msmexplorer.util.ui.FocusControlWithDeselect;
import edu.stanford.folding.msmexplorer.util.render.TextImageItemRenderer;
import java.awt.Point;
import java.awt.geom.Rectangle2D;
import javax.swing.BorderFactory;
import javax.swing.JOptionPane;
import prefuse.action.filter.VisibilityFilter;
import prefuse.action.layout.AxisLabelLayout;
import prefuse.render.AxisRenderer;
import prefuse.render.RendererFactory;
import prefuse.visual.expression.VisiblePredicate;
import prefuse.render.Renderer;
import prefuse.visual.expression.InGroupPredicate;
import prefuse.visual.sort.ItemSorter;


/**
 * Interface for visualizing graphs produced by TPT Theory.
 * Automatically initializes a @TPTFactory over the provided data
 * and retrieves top @numPaths paths.
 *
 * @author brycecr
 */
public class TPTWindow extends JFrame {

	private int numPaths = 5; //Number of initial top paths to retrieve
	private Visualization m_vis; 
	private boolean isShowingPics; //Display is showing images

	private static final String nodes = "graph.nodes";
	private static final String edges = "graph.edges";

	private static final int MAX_DEPTH = 100;
	private static final int AXIS_WIDTH = 40;

	private boolean axisVisible = true;
	
	/**
	 * Overloaded constructor to allow the use of
	 * ArrayLists of tuples in the place of TupleSets.
	 *
	 * @param g
	 * @param source
	 * @param target
	 */
	public TPTWindow(final Graph g, ArrayList<Tuple> source, ArrayList<Tuple> target) {
		this(g, toTupleSet(source), toTupleSet(target));
	}

	public TPTWindow(final Graph g, TupleSet source, TupleSet target) {

		if (overlap (source, target)) {
			JOptionPane.showMessageDialog(this, "Source and target sets overlap. "
				+ "Please select the sets again.", "Overlaping TupleSets", JOptionPane.WARNING_MESSAGE);
			this.dispose();
		}

		//should create a deep copy of the graph
		final Graph gra = new Graph(g.getNodeTable(), g.getEdgeTable(), g.isDirected(), g.getNodeKeyField(), g.getEdgeSourceField(), g.getEdgeTargetField());
		//create data columns
		if (gra.getNodeTable().getColumnNumber("source") != -1) {
			revert(gra.getEdgeTable().getColumn("flux"));
			revert(gra.getNodeTable().getColumn("flux"));	
			revert(gra.getNodeTable().getColumn("source"));	
			revert(gra.getNodeTable().getColumn("target"));	
			revert(gra.getNodeTable().getColumn("inTPT"));	
			revert(gra.getEdgeTable().getColumn("inTPT"));	
			revert(gra.getNodeTable().getColumn("TPT Distance"));
		} else {

			gra.getNodeTable().addColumn("source", boolean.class, false);
			gra.getNodeTable().addColumn("target", boolean.class, false);
			//g.addColumn("inTPT", boolean.class, false);
			gra.getNodeTable().addColumn("inTPT", boolean.class, false);
			gra.getEdgeTable().addColumn("inTPT", boolean.class, false);
			gra.addColumn("flux", double.class, 0.0d);
			gra.getNodeTable().addColumn("TPT Distance", Integer.class);
		}

		final TPTFactoryCM tptCalc = new TPTFactoryCM(gra, source, target);

		for (Iterator tuples = source.tuples(); tuples.hasNext(); )
			((Tuple)tuples.next()).set("source", true);

		for (Iterator tuples = target.tuples(); tuples.hasNext(); )
			((Tuple)tuples.next()).set("target", true);


		setNumPaths(tptCalc, gra, numPaths);

		m_vis = new Visualization();
		m_vis.add("graph", gra);
		m_vis.setValue(nodes, null, VisualItem.SHAPE, Constants.SHAPE_ELLIPSE);

		final LabelRenderer lr = new LabelRenderer();
		lr.setRoundedCorner(8, 8);

		final EdgeRenderer er = new EdgeRenderer();
		er.setEdgeType(Constants.EDGE_TYPE_CURVE);

		final RendererFactory rf = new RendererFactory() {

			Renderer yrend = new AxisRenderer(Constants.LEFT, Constants.TOP);

			public Renderer getRenderer(VisualItem item) {
				if (item.isInGroup(nodes)) 
					return lr;
				else if (item.isInGroup(edges))
					return er;
				else if (axisVisible)
					return yrend;
				else
					return null;
			}
		};
		final RendererFactory arf = new RendererFactory() {

			Renderer yrend = new AxisRenderer(Constants.LEFT, Constants.TOP);
			Renderer sr = new ShapeRenderer();

			public Renderer getRenderer(VisualItem item) {
				if (item.isInGroup(nodes)) 
					return sr;
				else if (item.isInGroup(edges))
					return er;
				else if (axisVisible)
					return yrend;
				else
					return null;
			}
		};
		//final DefaultRendererFactory drf = new DefaultRendererFactory(sr);
		//((EdgeRenderer)drf.getDefaultEdgeRenderer()).setEdgeType(Constants.EDGE_TYPE_CURVE);
		//m_vis.setRendererFactory(drf);
		m_vis.setRendererFactory(rf);

		VisibilityFilter filter = new VisibilityFilter("graph", new ColumnExpression("inTPT"));

		BreadthFirstIterator bfi = new BreadthFirstIterator();
		bfi.init(source.tuples(), MAX_DEPTH, Constants.NODE_TRAVERSAL);
		
		// traverse the graph
		int maxd = 1;
		while ( bfi.hasNext() ) {
			VisualItem item = (VisualItem)bfi.next();
			int d = bfi.getDepth(item);
			if (d > maxd)
				maxd = d;

			item.set("TPT Distance", d);
		}
		maxd++;
		Iterator t_itr = target.tuples();
		while (t_itr.hasNext())
			((Tuple)t_itr.next()).set("TPT Distance", maxd);

		AxisLayout xaxis = new AxisLayout(nodes, "TPT Distance", Constants.X_AXIS, VisiblePredicate.TRUE);
		AxisLayout yaxis = new AxisLayout(nodes, "eqProb", Constants.Y_AXIS, VisiblePredicate.TRUE);

		Rectangle2D rect = new Rectangle2D.Double();
		Rectangle2D lrect = new Rectangle2D.Double();
		final AxisLabelLayout ylabels = new AxisLabelLayout("ylabels", yaxis);
		//xaxis.setLayoutBounds(rect);
		//yaxis.setLayoutBounds(rect);
		
		ActionList tptLayout = new ActionList(ActionList.INFINITY);
		tptLayout.add(new ForceDirectedLayout("graph"));
		tptLayout.add(new RepaintAction());
		tptLayout.add(filter);

		final ActionList axes = new ActionList();
		axes.add(filter);
		axes.add(xaxis);
		axes.add(yaxis);
		axes.add(ylabels);

		m_vis.setVisible("ylabels", new InGroupPredicate("ylabels"), false);

		final ActionList altAxes = new ActionList();
		altAxes.add(filter);
		altAxes.add(xaxis);
		altAxes.add(yaxis);

		ForceSimulator fsim = ((ForceDirectedLayout)tptLayout.get(0)).getForceSimulator();
		Force[] forces = fsim.getForces();

		final Force NBodyForce = forces[0];
		//final Force DragForce = forces[1];
		final Force SpringForce = forces[2];

		//Default force management
		NBodyForce.setMinValue(0, -40.0f);
		//            NBodyForce.setParameter(0, -40.0f);
		//            NBodyForce.setParameter(1, -1);
		//            SpringForce.setParameter(0, .00001f);
		SpringForce.setMinValue(0, .00000099f);
		SpringForce.setMaxValue(1, 1600f);
		SpringForce.setParameter(1, 200f);

		final ColorAction dataFill = new ColorAction(nodes,
			VisualItem.FILLCOLOR, ColorLib.rgb(124, 252, 0));
		dataFill.add(VisualItem.HOVER, ColorLib.rgb(80, 200, 0));
		dataFill.add(VisualItem.FIXED, ColorLib.rgb(0, 124, 252));


		ColorAction nodeStroke = new ColorAction(nodes, VisualItem.STROKECOLOR, ColorLib.rgb(255, 255, 255));
		nodeStroke.add(VisualItem.HOVER, ColorLib.rgb(226, 86, 0));
		nodeStroke.add(VisualItem.HIGHLIGHT, ColorLib.rgb(0, 200, 98));

		ColorAction text = new ColorAction(nodes,
			VisualItem.TEXTCOLOR, ColorLib.gray(0));

		ColorAction edgeColor = new ColorAction(edges,
			VisualItem.STROKECOLOR, ColorLib.gray(200));

		ColorAction edgeFill = new ColorAction(edges,
			VisualItem.FILLCOLOR, ColorLib.gray(200));

		final ColorAction altDataFill = new ColorAction(nodes, VisualItem.FILLCOLOR, ColorLib.rgb(124, 252, 0));
		altDataFill.add(ExpressionParser.predicate("[source]"), ColorLib.rgb(255, 0, 0));
		altDataFill.add(ExpressionParser.predicate("[target]"), ColorLib.rgb(0, 0, 255));

		final DataSizeAction edgeWeight = new DataSizeAction(edges, "flux", 10, Constants.LINEAR_SCALE);
		edgeWeight.setMinimumSize(1.0);
		edgeWeight.setMaximumSize(200);

		final ActionList color = new ActionList();
		color.add(edgeWeight);
		color.add(edgeFill);
		color.add(edgeColor);
		color.add(text);
		color.add(dataFill);

		final ActionList altColor = new ActionList();
		altColor.add(edgeWeight);
		altColor.add(edgeFill);
		altColor.add(edgeColor);
		altColor.add(text);
		altColor.add(altDataFill);

		m_vis.putAction("nodeStroke", nodeStroke);
		m_vis.putAction("tptLayout", tptLayout);
		m_vis.putAction("axes", axes);
		m_vis.putAction("color", color);


		final DataSizeAction nodeSize = new DataSizeAction(nodes,
			"flux", 20, Constants.LOG_SCALE);
		nodeSize.setMinimumSize(5);
		nodeSize.setMaximumSize(20.0);

		m_vis.putAction("nodeSize", nodeSize);

		Display display = new Display(m_vis);
		display.setSize(700,700);
		display.pan(350, 350);
		display.setForeground(Color.WHITE);
		display.setBackground(Color.WHITE);
		display.setHighQuality(true);

		display.addControlListener(new FocusControlWithDeselect(1));
		display.addControlListener(new DragControl());
		display.addControlListener(new PanControl());
		display.addControlListener(new ZoomControl());
		display.addControlListener(new ZoomToFitControl());
		display.addControlListener(new WheelZoomControl());
		display.addControlListener(new HoverActionControl("color"));

		//Puts labels at furthest z-distance
		display.setItemSorter( new ItemSorter() {
			@Override
			public int score(VisualItem i) {
				if (i.isInGroup("ylabels")) 
					return 0;
				else
					return super.score(i);
			}
		});
		
		Insets insets = display.getInsets();
		rect.setRect(insets.left + AXIS_WIDTH, insets.top, display.getWidth() - insets.right, display.getHeight() - insets.bottom);
		lrect.setRect(insets.left, insets.top, insets.left + AXIS_WIDTH, display.getHeight() - insets.bottom);


		JTextField numPathInput = new JTextField("Num Paths");
		numPathInput.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent ae) {
				String input = ((JTextField)ae.getSource()).getText();

				int d = 1;
				try {
					d = Integer.valueOf(input.trim()).intValue();
				} catch ( NumberFormatException nfe ) {
					System.err.println( "Illegal number format "
						+ "in JTextField numPathInput");
				}
				numPaths = d;
				tptCalc.reset();
				setNumPaths(tptCalc, gra, numPaths );

				m_vis.run("tptLayout");
				m_vis.run("axes");
				m_vis.run("color");
				m_vis.run("nodeSize");
			}
		});

		final DefaultTupleSet holder = new DefaultTupleSet();
		final TupleSet focusGroup = m_vis.getGroup(Visualization.FOCUS_ITEMS);
		final JToggleButton fixedBtn = new JToggleButton("Fix Position", false);
		fixedBtn.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent ae ){

				//TupleSet focused = m_vis.getFocusGroup(Visualization.FOCUS_ITEMS);
				if ( focusGroup.getTupleCount() == 0) return;
				Tuple toFocus = (Tuple)focusGroup.tuples().next();

				if ( ((JToggleButton)ae.getSource()).isSelected() ) {
					((VisualItem)toFocus).setFixed(true);
					holder.addTuple(toFocus);
				} else {
					((VisualItem)toFocus).setFixed(false);
					holder.removeTuple(toFocus);
				}

				m_vis.run("color");
			}
		});
		fixedBtn.setSelected(false);
		fixedBtn.setEnabled(false);

		JToggleButton axisMode = new JToggleButton("Show Axis", true);
		axisMode.addActionListener( new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				if (((JToggleButton)ae.getSource()).isSelected()) {
					ylabels.setEnabled(true);
					m_vis.run("axes");
				} else {
					m_vis.removeGroup("ylabels");
					ylabels.setEnabled(false);
				}
				TupleSet all = m_vis.getGroup(nodes);
				Iterator itr = all.tuples();
				while (itr.hasNext()) {
					VisualItem t = (VisualItem)itr.next();
					holder.addTuple((Tuple)t);
					t.setFixed(true);
		}
				//m_vis.run("tptLayout");
				//m_vis.run("color");
				//m_vis.run("nodeSize");
			}
		});

		JToggleButton colorMode = new JToggleButton("Color Mode", false);
		colorMode.addActionListener( new ActionListener() {
			public void actionPerformed(ActionEvent ae) {

				if (((JToggleButton)ae.getSource()).isSelected()) {
					m_vis.removeAction("color");
					m_vis.putAction("color", altColor);
				} else {
					m_vis.removeAction("color");
					m_vis.putAction("color", color);
				}
				m_vis.run("color");
			}
		});

		final TextImageItemRenderer tiir = new TextImageItemRenderer();
		JToggleButton togglePics = new JToggleButton("Show Images", false);
		togglePics.addActionListener( new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				JToggleButton tp = (JToggleButton) ae.getSource();
				if(!tp.isSelected()) {
					m_vis.setRendererFactory(rf);
					lr.setImageField(null);
					((DataSizeAction)m_vis.getAction("nodeSize")).setMaximumSize(50.0);
					isShowingPics = false;
					m_vis.run("nodeSize");
				} else {
					/*
					 * ((DataSizeAction)m_vis.getAction("nodeSize")).setMaximumSize(1.0);
					 * m_vis.run("nodeSize");
					 * sr.setImageField("image");
					 * sr.setImagePosition(Constants.TOP);
					 * double scale = m_vis.getDisplay(0).getScale();
					 * if (scale < 1.0d) scale = 1.0d;
					 * sr.getImageFactory().setMaxImageDimensions((int)(100*scale), (int)(100*scale));

					 */

					m_vis.setRendererFactory(new DefaultRendererFactory(tiir));
					double scale = m_vis.getDisplay(0).getScale();
					if (scale < 1.0d) scale = 1.0d;
					tiir.setMaxImageDimensions((int)(100*scale), (int)(100*scale));
					tiir.setImageSize(1 / (2 * scale));
					tiir.setRoundedCorner(8, 8);

					isShowingPics = true;
					gra.addColumn("null", String.class, null);

					nodeSize.setMinimumSize(1);
					nodeSize.setMaximumSize(1);
					m_vis.run("nodeSize");

				}
				m_vis.run("color");
			}
		});


		JButton exportDisplay = new JButton("Save Image");
		exportDisplay.addActionListener( new ExportDisplayAction(display) );



		focusGroup.addTupleSetListener(new TupleSetListener() {
			public void tupleSetChanged(TupleSet ts, Tuple[] add, Tuple[] rem)
{
				if (add.length > 0 && holder.containsTuple(add[0])) {//add[0].getBoolean(VisualItem.FIXED) )
					fixedBtn.setEnabled(true);
					fixedBtn.setSelected(true);
				} else if (add.length > 0) {
					fixedBtn.setEnabled(true);
					fixedBtn.setSelected(false);
				} else {
					fixedBtn.setEnabled(false);
				}

				m_vis.run("nodeStroke");
			}
		});

		JButton openAdj = new JButton("Force Panel");
		openAdj.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent ae) {
				JFrame forceFrame = new JFrame("Force Panel");

				ForceSimulator fsim = ((ForceDirectedLayout)((ActionList)m_vis.getAction("tptLayout")).get(0)).getForceSimulator();
				JForcePanel fPanel = new JForcePanel(fsim);
				forceFrame.add(fPanel);
				forceFrame.pack();
				forceFrame.setVisible(true);
			}
		});

		JToggleButton nodeMode = new JToggleButton("Node Labels");
		nodeMode.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent ae) {
				JToggleButton nodeMode = (JToggleButton) ae.getSource();
				if (!nodeMode.isSelected()) {
					if (isShowingPics) {
						tiir.setImageSpacing(0);
						tiir.setHorizontalPadding(0);
						tiir.setTextAttributeName("null");
					} else {
						m_vis.setRendererFactory(arf);
						m_vis.setValue(nodes, null, VisualItem.SHAPE, Constants.SHAPE_ELLIPSE);
					}
				} else {
					if (isShowingPics) {
						tiir.setImageSpacing(3);
						tiir.setHorizontalPadding(5);
						tiir.setTextAttributeName("label");
					} else {
						m_vis.setRendererFactory(rf);
					}
				}

			}
		});
		nodeMode.setSelected(true);


		JRangeSlider edgeWeightSlider = new JRangeSlider(1, 4000, 1, 800, Constants.ORIENT_TOP_BOTTOM);
		edgeWeightSlider.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				JRangeSlider slider = (JRangeSlider)e.getSource();
				edgeWeight.setMaximumSize(slider.getHighValue());
				edgeWeight.setMinimumSize(slider.getLowValue());
				m_vis.run("color");
			}
		});

		JRangeSlider nodeSizeSlider = new JRangeSlider(1, 1000, 1, 50, Constants.ORIENT_TOP_BOTTOM);
		nodeSizeSlider.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				JRangeSlider slider = (JRangeSlider)e.getSource();
				nodeSize.setMaximumSize(slider.getHighValue());
				nodeSize.setMinimumSize(slider.getLowValue());
				m_vis.run("nodeSize");
			}
		});

		Box buttonPanel = new Box(BoxLayout.X_AXIS);
		buttonPanel.add(fixedBtn);
		buttonPanel.add(togglePics);
		buttonPanel.add(exportDisplay);
		buttonPanel.add(colorMode);
		buttonPanel.add(axisMode);
		buttonPanel.add(openAdj);
		buttonPanel.add(nodeMode);
		buttonPanel.add(numPathInput);

		Box sliderPanel = new Box(BoxLayout.X_AXIS);
		sliderPanel.add(new JLabel("Edge Weight "));
		sliderPanel.add(edgeWeightSlider);
		sliderPanel.add(Box.createHorizontalStrut(40));
		sliderPanel.add(new JLabel("Node Size "));
		sliderPanel.add(nodeSizeSlider);
		sliderPanel.add(Box.createHorizontalStrut(10));

		Box southPanel = new Box(BoxLayout.Y_AXIS);
		southPanel.add(buttonPanel);
		southPanel.add(sliderPanel);

		display.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));
		JPanel panel = new JPanel(new BorderLayout());
		panel.add(southPanel, BorderLayout.SOUTH);
		panel.add(display, BorderLayout.CENTER);
		panel.setBackground(Color.white);

		this.setContentPane(panel);

		m_vis.run("tptLayout");
		m_vis.run("axes");
		TupleSet all = m_vis.getGroup(nodes);
		Iterator itr = all.tuples();
		while (itr.hasNext()) {
			VisualItem t = (VisualItem)itr.next();
			holder.addTuple((Tuple)t);
			t.setFixed(true);
		}
		m_vis.run("color");
		m_vis.run("nodeSize");

		//adjusts so scales fit inside window. Seems slicker than setting bounds for the axes...maybe...
		//display.animateZoomAbs(display.getLocation(new Point(display.getWidth() / 2, display.getHeight() / 2)), .9, 500);

		this.pack();
		this.setTitle("T P T V i e w  |  M S M E x p l o r e r");
		this.setVisible(true);
		this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
	}

	private void setNumPaths( TPTFactoryCM tptCalc, Graph g, int numPaths ) {

		Column edgeField = g.getEdgeTable().getColumn("inTPT");
		Column  nodeField = g.getNodeTable().getColumn("inTPT");

		for ( int i = 0; i < edgeField.getRowCount(); ++i )
			edgeField.revertToDefault(i);

		for ( int i = 0; i < nodeField.getRowCount(); ++i )
			nodeField.revertToDefault(i);

		for (int i = 0; i < numPaths; ++i) {

			ArrayList<Edge> path = tptCalc.getNextEdge();
			if (path.isEmpty()) continue;

			path.get(0).getTargetNode().set("inTPT", true);
			for (Edge e : path) {
				e.set("inTPT", true);
				e.getSourceNode().set("inTPT", true);
			}
		}
	}

	private void processColumn( Column col, int size ) {

		double max = max(col);
		double min = min(col);

		double[] palette = new double[size+1];

		for (int i = 0; i < size+1; ++i)
			palette[i] = 2*i + 1.0d;

		double step = (max - min) / size;
		double base = 1.0;

		for (int i = 0; i < col.getRowCount(); ++i)
			col.setDouble(palette[(int)(col.getDouble(i) / step)], i);
	}

	private double max( Column col ) {

		double max = col.getDouble(0);

		for (int i = 1; i < col.getRowCount(); ++i)
			if (col.getDouble(i) > max)
				max = col.getDouble(i);

		return max;
	}

	/**
	 * Return smallest double in the column
	 * 
	 * @param column of doubles
	 * @return minimum double value
	 */
	private double min( Column col ) {

		double min = col.getDouble(0);

		for (int i = 1; i < col.getRowCount(); ++i)
			if (col.getDouble(i) < min)
				min = col.getDouble(i);

		return min;
	}

	/**
	 * Returns TupleSet containing members of ArrayList
	 * 
	 * @param ArrayList of tuples to put in TupleSet
	 * @return resulting TupleSet
	 */
	public static TupleSet toTupleSet( ArrayList<Tuple> source ) {

		TupleSet set = new DefaultTupleSet();

		for (Tuple t : source)
			set.addTuple(t);

		return set;
	}

	public static void revert (Column column) {
		for (int i = 0; i < column.getRowCount(); ++i)
			column.revertToDefault(i);
	}

	public static boolean overlap (TupleSet one, TupleSet two) {
		if (one.equals(two))
			return true;
		
		Iterator iOne = one.tuples();
		Iterator iTwo = two.tuples();

		while (iOne.hasNext() && iTwo.hasNext()) 
			if (iOne.next().equals(iTwo.next()))
				return true;

		return false;	
	}
}