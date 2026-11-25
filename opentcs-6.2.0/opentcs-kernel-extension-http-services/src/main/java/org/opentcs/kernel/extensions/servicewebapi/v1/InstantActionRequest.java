package org.opentcs.kernel.extensions.servicewebapi.v1;

import java.util.List;

public class InstantActionRequest {

  private List<Action> actions;
  private long headerId;
  private String timeStamp;
  private String version;
  private String manufacturer;
  private String serialNumber;

  public InstantActionRequest() {
    // Default constructor
  }

  public List<Action> getActions() {
    return actions;
  }

  public void setActions(List<Action> actions) {
    this.actions = actions;
  }

  public long getHeaderId() {
    return headerId;
  }

  public void setHeaderId(long headerId) {
    this.headerId = headerId;
  }

  public String getTimeStamp() {
    return timeStamp;
  }

  public void setTimeStamp(String timeStamp) {
    this.timeStamp = timeStamp;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public String getManufacturer() {
    return manufacturer;
  }

  public void setManufacturer(String manufacturer) {
    this.manufacturer = manufacturer;
  }

  public String getSerialNumber() {
    return serialNumber;
  }

  public void setSerialNumber(String serialNumber) {
    this.serialNumber = serialNumber;
  }

  public static class Action {
    private String actionType;
    private String actionId;
    private String blockingType;
    private List<ActionParameter> actionParameters;
    private String actionDescription;

    public Action() {
      // Default constructor
    }

    public String getActionType() {
      return actionType;
    }

    public void setActionType(String actionType) {
      this.actionType = actionType;
    }

    public String getActionId() {
      return actionId;
    }

    public void setActionId(String actionId) {
      this.actionId = actionId;
    }

    public String getBlockingType() {
      return blockingType;
    }

    public void setBlockingType(String blockingType) {
      this.blockingType = blockingType;
    }

    public List<ActionParameter> getActionParameters() {
      return actionParameters;
    }

    public void setActionParameters(List<ActionParameter> actionParameters) {
      this.actionParameters = actionParameters;
    }

    public String getActionDescription() {
      return actionDescription;
    }

    public void setActionDescription(String actionDescription) {
      this.actionDescription = actionDescription;
    }
  }

  public static class ActionParameter {
    private String key;
    private String value;

    public ActionParameter() {
      // Default constructor
    }

    public String getKey() {
      return key;
    }

    public void setKey(String key) {
      this.key = key;
    }

    public String getValue() {
      return value;
    }

    public void setValue(String value) {
      this.value = value;
    }
  }
}
