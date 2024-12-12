package com.flomaestro.engine.pi;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.List;

public class DebuggerUtil {

  public static boolean isDebuggerAttached() {
    RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
    List<String> arguments = runtimeMXBean.getInputArguments();

    for (String argument : arguments) {
      if (argument.contains("-agentlib:jdwp")) {
        return true;
      }
    }
    return false;
  }
}
