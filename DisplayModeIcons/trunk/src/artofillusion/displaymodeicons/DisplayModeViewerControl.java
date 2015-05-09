/*
 *  Copyright 2007 Francois Guillet
 *  This program is free software; you can redistribute it and/or modify it under the
 *  terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 2 of the License, or (at your option) any later version.
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 */
   
 /* This version was edited by Petri Ihalainen in 2015 */

package artofillusion.displaymodeicons;

import java.awt.Dimension;
import java.awt.Insets;

import buoy.event.ValueChangedEvent;
import buoy.widget.LayoutInfo;
import buoy.widget.RowContainer;
import buoy.widget.Widget;
import buoy.widget.BFrame;
import artofillusion.ViewerCanvas;
import artofillusion.ui.ThemeManager;
import artofillusion.ui.ToolButtonWidget;
import artofillusion.view.ViewChangedEvent;
import artofillusion.view.ViewerControl;
import artofillusion.ui.EditingWindow;
import artofillusion.*;

public class DisplayModeViewerControl implements ViewerControl {
	
	// These three are common to all four copies of the icons in one window
	// I tried to use beforeRenderMode = -1 as an indicator that none of the 
	// views has been set to 'rendered' during this session yet, but that produced some 
	// strange behaviour. Using the dedicated boolean instead.
	
	ViewerCanvas currentRendedred = null;
	int beforeRenderMode = ViewerCanvas.RENDER_FLAT;
	boolean renderedBefore = false;
	
	public Widget createWidget(ViewerCanvas canvas) {

		return new DisplayModeViewerControlWidget(canvas);
	}

	public String getName() {
		return ("Display Mode Buttons");
	}
	
	public class DisplayModeViewerControlWidget extends RowContainer {
		
		private ViewerCanvas canvas;

		private ToolButtonWidget[] buttons;
		
		public DisplayModeViewerControlWidget(ViewerCanvas canvas) {
			super();
			this.canvas = canvas;
			
			canvas.addEventLink(ViewChangedEvent.class, this, "reflectCanvasState");
			buttons = new ToolButtonWidget[6];
			buttons[0] = new ToolButtonWidget(ThemeManager.getToolButton(this, "displaymodeicons:wireframe"));
			buttons[1] = new ToolButtonWidget(ThemeManager.getToolButton(this, "displaymodeicons:shaded"));
			buttons[2] = new ToolButtonWidget(ThemeManager.getToolButton(this, "displaymodeicons:smooth"));
			buttons[3] = new ToolButtonWidget(ThemeManager.getToolButton(this, "displaymodeicons:textured"));
			buttons[4] = new ToolButtonWidget(ThemeManager.getToolButton(this, "displaymodeicons:transparent"));			
			buttons[5] = new ToolButtonWidget(ThemeManager.getToolButton(this, "displaymodeicons:rendered"));	
			
			add(buttons[0], new LayoutInfo(LayoutInfo.CENTER, LayoutInfo.NONE, new Insets(0, 3, 0, 0), new Dimension(0,0)));//???
			for (int i = 1; i < 5; i++) {
				add(buttons[i]);
			}
			
			add(buttons[5], new LayoutInfo(LayoutInfo.CENTER, LayoutInfo.NONE, new Insets(0, 0, 0, 3), new Dimension(0,0)));
			
			for (int i = 0; i < 6; i++) {
				buttons[i].addEventLink(ValueChangedEvent.class, this, "doButtonPressed");
			}
			reflectCanvasState();
		}

		private void reflectCanvasState() {

			checkRenderMode(canvas); // Check if this canvas is in 'rendered' mode
			
			for (int i = 0; i < 6; i++) {
				buttons[i].setSelected(false);
			}
			switch (canvas.getRenderMode()) {
			case ViewerCanvas.RENDER_WIREFRAME :
				buttons[0].setSelected(true);
				break;
			case ViewerCanvas.RENDER_FLAT :
				buttons[1].setSelected(true);
				break;
			case ViewerCanvas.RENDER_SMOOTH :
				buttons[2].setSelected(true);
				break;
			case ViewerCanvas.RENDER_TEXTURED :
				buttons[3].setSelected(true);
				break;
			case ViewerCanvas.RENDER_TRANSPARENT:
				buttons[4].setSelected(true);
				break;
			case ViewerCanvas.RENDER_RENDERED:
				buttons[5].setSelected(true);
				break;
			}
		}

		private void doButtonPressed(ValueChangedEvent ev) {
			
			ToolButtonWidget tbw = (ToolButtonWidget) ev.getWidget();
			if (!tbw.isSelected()) {
				//select the button back
				tbw.setSelected(true);
			} else {
				for (int i = 0; i < 6; i++) {
					if (buttons[i] != tbw && buttons[i].isSelected()) {
						buttons[i].setSelected(false);
					} else if (buttons[i] == tbw) {
						switch(i) {
						case 0:
							unsetThisCanvasRendered(canvas);
							canvas.setRenderMode(ViewerCanvas.RENDER_WIREFRAME);
							break;
						case 1:
							unsetThisCanvasRendered(canvas);
							canvas.setRenderMode(ViewerCanvas.RENDER_FLAT);
							break;
						case 2:
							unsetThisCanvasRendered(canvas);
							canvas.setRenderMode(ViewerCanvas.RENDER_SMOOTH);
							break;
						case 3:
							canvas.setRenderMode(ViewerCanvas.RENDER_TEXTURED);
							break;
						case 4:
							unsetThisCanvasRendered(canvas);
							canvas.setRenderMode(ViewerCanvas.RENDER_TRANSPARENT);
							break;
						case 5:
							if (currentRendedred != canvas)
							{	
								if (! renderedBefore) 
								{
									beforeRenderMode = canvas.getRenderMode(); // if the view does not have a "before mode" then use the one of the next "rendered" view
									renderedBefore = true;
								}
								if (currentRendedred != null)
									currentRendedred.setRenderMode(beforeRenderMode);
								beforeRenderMode = canvas.getRenderMode();
								//currentRendedred = canvas; // reflectCanvasState is taking care of this
							}
							canvas.setRenderMode(ViewerCanvas.RENDER_RENDERED);
							break;
						}
					}
				}
			}
		}
		
		private void unsetThisCanvasRendered(ViewerCanvas canvas)
		{	
			if (currentRendedred == canvas)
				currentRendedred = null;
		}
		
		private void checkRenderMode(ViewerCanvas canvas)
		{	
			if(canvas.getRenderMode() == ViewerCanvas.RENDER_RENDERED)
				currentRendedred = canvas;
		}
	}
}
