package com.mapbox.services.android.navigation.v5.location;

public class LoggingLocation {

  private String accuracy;
  private String worseAccuracyAcceptable;
  private String distanceBetweenUpdates;
  private String timeSinceLastUpdate;
  private String velocity;
  private String validVelocity;
  private String validLocation;

  public String getAccuracy() {
    return accuracy;
  }

  public void setAccuracy(String accuracy) {
    this.accuracy = accuracy;
  }

  public String getWorseAccuracyAcceptable() {
    return worseAccuracyAcceptable;
  }

  public void setWorseAccuracyAcceptable(String worseAccuracyAcceptable) {
    this.worseAccuracyAcceptable = worseAccuracyAcceptable;
  }

  public String getDistanceBetweenUpdates() {
    return distanceBetweenUpdates;
  }

  public void setDistanceBetweenUpdates(String distanceBetweenUpdates) {
    this.distanceBetweenUpdates = distanceBetweenUpdates;
  }

  public String getTimeSinceLastUpdate() {
    return timeSinceLastUpdate;
  }

  public void setTimeSinceLastUpdate(String timeSinceLastUpdate) {
    this.timeSinceLastUpdate = timeSinceLastUpdate;
  }

  public String getVelocity() {
    return velocity;
  }

  public void setVelocity(String velocity) {
    this.velocity = velocity;
  }

  public String getValidVelocity() {
    return validVelocity;
  }

  public void setValidVelocity(String validVelocity) {
    this.validVelocity = validVelocity;
  }

  public String getValidLocation() {
    return validLocation;
  }

  public void setValidLocation(String validLocation) {
    this.validLocation = validLocation;
  }
}
