/*
 *  Copyright 2007 Francois Guillet
 *  This program is free software; you can redistribute it and/or modify it under the
 *  terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 2 of the License, or (at your option) any later version.
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 */
 
 /* This version was edited by Petri Ihalainen in 2015 to bring it up to date with the main program
  * Now it can 
  * - find the DisplayMode-menu anywhere in the menu bar
  * - provide the same behaviour in all editor windows
  * - handle the "rendered" mode
  */
 
package artofillusion.displaymodeicons;

import java.util.Iterator;
import java.util.List;

import buoy.event.CommandEvent;
import buoy.widget.BCheckBoxMenuItem;
import buoy.widget.BMenu;
import buoy.widget.BMenuBar;
import buoy.widget.BFrame;
import buoy.widget.Widget;

import artofillusion.ui.EditingWindow;
import artofillusion.*;
import artofillusion.Plugin;
import artofillusion.ViewerCanvas;
import artofillusion.view.ViewerControl;
import artofillusion.view.ViewerPerspectiveControl;

public class DisplayModeIconsPlugin implements Plugin {
	
    /**
     *  Process messages sent to plugin by AoI (see AoI API description)
     *
     *@param  message  The message
     *@param  args     Arguments depending on the message
     */
	
    public void processMessage( int message, Object args[] ) 
	{
	 System.out.println("DisplayModeIcons starting...");
	 
	  if ( message == Plugin.APPLICATION_STARTING ) 
	  {  
	     //first, let's remove previous controls
	     List controls = ViewerCanvas.getViewerControls();
	     for (int i = 0; i < controls.size(); i++) 
		 {
		   ViewerControl control = (ViewerControl) controls.get(i);
		   if (control instanceof ViewerPerspectiveControl) 
		      ViewerCanvas.removeViewerControl(control);
	     }
		 
		 // Then add the icon controls
	     ViewerCanvas.addViewerControl(new DisplayModeViewerControl());
	     ViewerCanvas.addViewerControl(new GridViewerControl());
	     ViewerCanvas.addViewerControl(new AxesViewerControl());
	     ViewerCanvas.addViewerControl(new PerspectiveViewerControl());
	  }
	 
	  else if (message == Plugin.SCENE_WINDOW_CREATED || message == Plugin.OBJECT_WINDOW_CREATED) {
		 
	    BFrame window = (BFrame) args[0];

		BMenu menu = getDisplayModeMenu(window); // This is new
		if (menu != null)
		{
	      AllViewsDisplayMenuManager manager = new AllViewsDisplayMenuManager(window);
		  
		  for (int child = 0; child < 5; child++)
		    {
	           ((BCheckBoxMenuItem)(menu.getChild(child))).removeEventLink(CommandEvent.class, window);			   
	           ((BCheckBoxMenuItem)(menu.getChild(child))).addEventLink(CommandEvent.class, manager, "doProcess");
		    }
			
		  // removing the 'rendered' mode from the menu, because 
		  // only one view at a time can be in rendered mode.
		  
		  if (menu.getChildCount() > 5) // The boolean modelling window does not have 'rendered' option
			menu.remove((Widget)menu.getChild(5));
		}
	  }
    }
	 
    private class AllViewsDisplayMenuManager {
	BFrame window;

	public AllViewsDisplayMenuManager(BFrame w) {
	    window = w;
	}

	public void doProcess(CommandEvent ev) {
		
		BMenu menu = getDisplayModeMenu(window);
		if (menu == null) return;
		
	    BCheckBoxMenuItem[] items = new BCheckBoxMenuItem[5];
	    for (int i = 0; i < 5; i++) {
		items[i] = (BCheckBoxMenuItem)(menu.getChild(i));
		items[i].setState(false);
	    }
	    Widget w = ev.getWidget();
	    int mode = -1;
	    for (int i = 0; i < 5; i++) {
		if (items[i] == w) {
		    switch(i) {
		    case 0:
			mode = ViewerCanvas.RENDER_WIREFRAME;
			break;
		    case 1:
			mode = ViewerCanvas.RENDER_FLAT;
			break;
		    case 2:
			mode = ViewerCanvas.RENDER_SMOOTH;
			break;
		    case 3:
			mode = ViewerCanvas.RENDER_TEXTURED;
			break;
		    case 4:
			mode = ViewerCanvas.RENDER_TRANSPARENT;
			break;
		    }
		    items[i].setState(true);
		}
	    }
	    ViewerCanvas[] views = ((EditingWindow)window).getAllViews();
	    if (mode > -1) for (int i = 0; i < 4; i++) {
		views[i].setRenderMode(mode);
	    }
	}
    }

	public BMenu getDisplayModeMenu(BFrame window)
	{
		// This should find the DisplayMode-menu anywhere in the menuBar
		// (directly in the menuBar or under a sub-menu -- this is not recursive any further.)
		
		BMenuBar menuBar = window.getMenuBar();
	    BMenu barMenu, menu=null;
		
		for (int i = 0; i < menuBar.getChildCount(); i++)
		{
			barMenu = menuBar.getChild(i);
			if (barMenu instanceof BMenu && ((BMenu)barMenu).getName().endsWith("displayMode"))
				return barMenu;
			else
			{
				for (int j = 0; j < barMenu.getChildCount(); j++)
				  if (barMenu.getChild(j) instanceof BMenu)
				    if (((BMenu)barMenu.getChild(j)).getName().endsWith("displayMode"))
				      menu = (BMenu)barMenu.getChild(j);
			}
		}		
		return menu;
	}
}
