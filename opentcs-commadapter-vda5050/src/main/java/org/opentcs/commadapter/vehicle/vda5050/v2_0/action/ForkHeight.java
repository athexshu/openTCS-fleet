/**
 * Copyright (c) The openTCS Authors.
 *
 * This program is free software and subject to the MIT license. (For details,
 * see the licensing information (LICENSE.txt) you should have received with
 * this copy of the software.)
 */
package org.opentcs.commadapter.vehicle.vda5050.v2_0.action;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.opentcs.commadapter.vehicle.vda5050.v2_0.message.common.Action;
import org.opentcs.commadapter.vehicle.vda5050.v2_0.message.common.ActionParameter;
import org.opentcs.commadapter.vehicle.vda5050.v2_0.message.common.BlockingType;

/**
 * The VDA5050 {@code ForkHeight} instant action.
 */
public class ForkHeight
    extends
      Action {

  /**
   * This action's action type.
   */
  public static final String ACTION_TYPE = "ForkHeight";

  /**
   * The key of the (VDA5050) {@code end_height} parameter for this action.
   */
  public static final String PARAMKEY_END_HEIGHT = "end_height";

  /**
   * The key of the (VDA5050) {@code recognize} parameter for this action.
   */
  public static final String PARAMKEY_RECOGNIZE = "recognize";

  @SuppressWarnings("this-escape")
  public ForkHeight(
      @Nonnull
      String actionId,
      @Nonnull
      BlockingType blockingType,
      @Nullable
      String paramValueEndHeight,
      @Nullable
      String paramValueRecognize
  ) {
    super(ACTION_TYPE, actionId, blockingType);

    List<ActionParameter> actionParameters = new ArrayList<>();
    if (paramValueEndHeight != null) {
      actionParameters.add(new ActionParameter(PARAMKEY_END_HEIGHT, paramValueEndHeight));
    }
    if (paramValueRecognize != null) {
      actionParameters.add(new ActionParameter(PARAMKEY_RECOGNIZE, paramValueRecognize));
    }
    if (!actionParameters.isEmpty()) {
      setActionParameters(actionParameters);
    }
  }
}
