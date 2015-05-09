/*
 *  Copyright 2007 Francois Guillet
 *  This program is free software; you can redistribute it and/or modify it under the
 *  terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 2 of the License, or (at your option) any later version.
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 */

 /* Name changed from 'Toggle Grid Button' to 'Grid Toggle Button' 2015 -- Pete*/
 
package artofillusion.displaymodeicons;

import java.awt.Dimension;
import java.awt.Insets;

import buoy.event.ValueChangedEvent;
import buoy.widget.LayoutInfo;
import buoy.widget.RowContainer;
import buoy.widget.Widget;
import artofillusion.ViewerCanvas;
import artofillusion.ui.ThemeManager;
import artofillusion.ui.ToolButtonWidget;
import artofillusion.view.ViewChangedEvent;
import artofillusion.view.ViewerControl;

public class GridViewerControl implements ViewerControl {

	public Widget createWidget(ViewerCanvas canvas) {
		return new GridViewerControlWidget(canvas);
	}

	public String getName() {
		return ("Grid Toggle Button");
	}
	
	public class GridViewerControlWidget extends RowContainer {
		
		private ViewerCanvas canvas;
		private ToolButtonWidget button;
		
		public GridViewerControlWidget(ViewerCanvas canvas) {
			super();
			this.canvas = canvas;
			canvas.addEventLink(ViewChangedEvent.class, this, "reflectCanvasState");
			button = new ToolButtonWidget(ThemeManager.getToolButton(this, "displaymodeicons:grid"));
			button.addEventLink(ValueChangedEvent.class, this, "doButtonPressed");
			reflectCanvasState();
			setDefaultLayout(new LayoutInfo(LayoutInfo.CENTER, LayoutInfo.NONE, new Insets(0, 3, 0, 3), new Dimension(0,0)));
			add(button);
		}

		private void reflectCanvasState() {
			button.setSelected(canvas.getShowGrid());
		}
		
		private void doButtonPressed() {
			canvas.setGrid(canvas.getGridSpacing(), canvas.getSnapToSubdivisions(), button.isSelected(), canvas.getSnapToGrid());
			canvas.repaint();
		}
	}
}
