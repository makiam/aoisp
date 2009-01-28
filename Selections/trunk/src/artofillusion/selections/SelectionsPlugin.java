/* Copyright (C) 2009 by Peter Eastman

   This program is free software; you can redistribute it and/or modify it under the
   terms of the GNU General Public License as published by the Free Software
   Foundation; either version 2 of the License, or (at your option) any later version.

   This program is distributed in the hope that it will be useful, but WITHOUT ANY 
   WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
   PARTICULAR PURPOSE.  See the GNU General Public License for more details. */

package artofillusion.selections;

import artofillusion.*;
import buoy.widget.*;
import buoyx.docking.*;

/** This is the main entry point for the SelectionsPlugin. */

public class SelectionsPlugin implements Plugin
{
  public void processMessage(int message, Object args[])
  {
    if (message == SCENE_WINDOW_CREATED)
    {
      LayoutWindow window = (LayoutWindow) args[0];
      DockableWidget widget = new DockableWidget(new SelectionsPanel(window), "Selections");
      window.getDockingContainer(BTabbedPane.RIGHT).addDockableWidget(widget);
    }
  }
}