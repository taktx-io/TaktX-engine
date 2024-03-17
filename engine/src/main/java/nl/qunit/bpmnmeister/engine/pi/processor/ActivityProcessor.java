package nl.qunit.bpmnmeister.engine.pi.processor;

import nl.qunit.bpmnmeister.pd.model.Activity;
import nl.qunit.bpmnmeister.pi.state.ActivityState;
import org.jboss.logging.Logger;

public abstract class ActivityProcessor<E extends Activity, S extends ActivityState>
    extends StateProcessor<E, S> {
  private static final Logger LOG = Logger.getLogger(ActivityProcessor.class);
}
