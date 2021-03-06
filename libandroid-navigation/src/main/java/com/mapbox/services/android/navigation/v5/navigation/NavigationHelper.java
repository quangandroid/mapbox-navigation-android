package com.mapbox.services.android.navigation.v5.navigation;

import android.location.Location;

import com.mapbox.api.directions.v5.models.DirectionsRoute;
import com.mapbox.api.directions.v5.models.LegStep;
import com.mapbox.api.directions.v5.models.StepManeuver;
import com.mapbox.core.constants.Constants;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;
import com.mapbox.services.android.navigation.v5.milestone.Milestone;
import com.mapbox.services.android.navigation.v5.offroute.OffRoute;
import com.mapbox.services.android.navigation.v5.offroute.OffRouteCallback;
import com.mapbox.services.android.navigation.v5.offroute.OffRouteDetector;
import com.mapbox.services.android.navigation.v5.route.FasterRoute;
import com.mapbox.services.android.navigation.v5.routeprogress.RouteProgress;
import com.mapbox.services.android.navigation.v5.snap.Snap;
import com.mapbox.services.android.telemetry.utils.MathUtils;
import com.mapbox.turf.TurfConstants;
import com.mapbox.turf.TurfMeasurement;
import com.mapbox.turf.TurfMisc;

import java.util.ArrayList;
import java.util.List;

/**
 * This contains several single purpose methods that help out when a new location update occurs and
 * calculations need to be performed on it.
 */
class NavigationHelper {

  private NavigationHelper() {
    // Empty private constructor to prevent users creating an instance of this class.
  }

  /**
   * Takes in a raw location, converts it to a point, and snaps it to the closest point along the
   * route. This is isolated as separate logic from the snap logic provided because we will always
   * need to snap to the route in order to get the most accurate information.
   */
  static Point userSnappedToRoutePosition(Location location, List<Point> coordinates) {
    if (coordinates.size() < 2) {
      return Point.fromLngLat(location.getLongitude(), location.getLatitude());
    }

    Point locationToPoint = Point.fromLngLat(location.getLongitude(), location.getLatitude());

    // Uses Turf's pointOnLine, which takes a Point and a LineString to calculate the closest
    // Point on the LineString.
    Feature feature = TurfMisc.nearestPointOnLine(locationToPoint, coordinates);
    return ((Point) feature.geometry());
  }

  /**
   * When a milestones triggered, it's instruction needs to be built either using the provided
   * string or an empty string.
   */
  static String buildInstructionString(RouteProgress routeProgress, Milestone milestone) {
    if (milestone.getInstruction() != null) {
      // Create a new custom instruction based on the Instruction packaged with the Milestone
      return milestone.getInstruction().buildInstruction(routeProgress);
    }
    return "";
  }

  /**
   * Calculates the distance remaining in the step from the current users snapped position, to the
   * next maneuver position.
   */
  static double stepDistanceRemaining(Point snappedPosition, int legIndex, int stepIndex,
                                      DirectionsRoute directionsRoute, List<Point> coordinates) {
    List<LegStep> steps = directionsRoute.legs().get(legIndex).steps();
    Point nextManeuverPosition = nextManeuverPosition(stepIndex, steps, coordinates);

    LineString lineString = LineString.fromPolyline(steps.get(stepIndex).geometry(),
      Constants.PRECISION_6);
    // If the users snapped position equals the next maneuver
    // position or the linestring coordinate size is less than 2,the distance remaining is zero.
    if (snappedPosition.equals(nextManeuverPosition) || lineString.coordinates().size() < 2) {
      return 0;
    }
    LineString slicedLine = TurfMisc.lineSlice(snappedPosition, nextManeuverPosition, lineString);
    return TurfMeasurement.length(slicedLine, TurfConstants.UNIT_METERS);
  }

  /**
   * Takes in the already calculated step distance and iterates through the step list from the
   * step index value plus one till the end of the leg.
   */
  static double legDistanceRemaining(double stepDistanceRemaining, int legIndex, int stepIndex,
                                     DirectionsRoute directionsRoute) {
    List<LegStep> steps = directionsRoute.legs().get(legIndex).steps();
    if ((steps.size() < stepIndex + 1)) {
      return stepDistanceRemaining;
    }
    for (int i = stepIndex + 1; i < steps.size(); i++) {
      stepDistanceRemaining += steps.get(i).distance();
    }
    return stepDistanceRemaining;
  }

  /**
   * Takes in the leg distance remaining value already calculated and if additional legs need to be
   * traversed along after the current one, adds those distances and returns the new distance.
   * Otherwise, if the route only contains one leg or the users on the last leg, this value will
   * equal the leg distance remaining.
   */
  static double routeDistanceRemaining(double legDistanceRemaining, int legIndex,
                                       DirectionsRoute directionsRoute) {
    if (directionsRoute.legs().size() < 2) {
      return legDistanceRemaining;
    }

    for (int i = legIndex + 1; i < directionsRoute.legs().size(); i++) {
      legDistanceRemaining += directionsRoute.legs().get(i).distance();
    }
    return legDistanceRemaining;
  }

  /**
   * Checks whether the user's bearing matches the next step's maneuver provided bearingAfter
   * variable. This is one of the criteria's required for the user location to be recognized as
   * being on the next step or potentially arriving.
   * <p>
   * If the expected turn angle is less than the max turn completion offset, this method will
   * wait for the step distance remaining to be 0.  This way, the step index does not increase
   * prematurely.
   *
   * @param userLocation          the location of the user
   * @param previousRouteProgress used for getting the most recent route information
   * @return boolean true if the user location matches (using a tolerance) the final heading
   * @since 0.2.0
   */
  static boolean checkBearingForStepCompletion(Location userLocation, RouteProgress previousRouteProgress,
                                               double stepDistanceRemaining, double maxTurnCompletionOffset) {
    if (previousRouteProgress.currentLegProgress().upComingStep() == null) {
      return false;
    }

    // Bearings need to be normalized so when the bearingAfter is 359 and the user heading is 1, we
    // count this as within the MAXIMUM_ALLOWED_DEGREE_OFFSET_FOR_TURN_COMPLETION.
    StepManeuver maneuver = previousRouteProgress.currentLegProgress().upComingStep().maneuver();
    double initialBearing = maneuver.bearingBefore();
    double initialBearingNormalized = MathUtils.wrap(initialBearing, 0, 360);
    double finalBearing = maneuver.bearingAfter();
    double finalBearingNormalized = MathUtils.wrap(finalBearing, 0, 360);

    double expectedTurnAngle = MathUtils.differenceBetweenAngles(initialBearingNormalized, finalBearingNormalized);

    double userBearingNormalized = MathUtils.wrap(userLocation.getBearing(), 0, 360);
    double userAngleFromFinalBearing = MathUtils.differenceBetweenAngles(finalBearingNormalized, userBearingNormalized);

    if (expectedTurnAngle <= maxTurnCompletionOffset) {
      return stepDistanceRemaining == 0;
    } else {
      return userAngleFromFinalBearing <= maxTurnCompletionOffset;
    }
  }

  /**
   * This is used when a user has completed a step maneuver and the indices need to be incremented.
   * The main purpose of this class is to determine if an additional leg exist and the step index
   * has met the first legs total size, a leg index needs to occur and step index should be reset.
   * Otherwise, the step index is incremented while the leg index remains the same.
   * <p>
   * Rather than returning an int array, a new instance of Navigation Indices gets returned. This
   * provides type safety and making the code a bit more readable.
   * </p>
   *
   * @param routeProgress   need a routeProgress in order to get the directions route leg list size
   * @param previousIndices used for adjusting the indices
   * @return a {@link NavigationIndices} object which contains the new leg and step indices
   */
  static NavigationIndices increaseIndex(RouteProgress routeProgress,
                                         NavigationIndices previousIndices) {
    DirectionsRoute route = routeProgress.directionsRoute();
    int previousStepIndex = previousIndices.stepIndex();
    int previousLegIndex = previousIndices.legIndex();
    int routeLegSize = route.legs().size();
    int legStepSize = route.legs().get(routeProgress.legIndex()).steps().size();

    boolean isOnLastLeg = previousLegIndex == routeLegSize - 1;
    boolean isOnLastStep = previousStepIndex == legStepSize - 1;

    if (isOnLastStep && !isOnLastLeg) {
      return NavigationIndices.create((previousLegIndex + 1), 0);
    }

    if (isOnLastStep) {
      return previousIndices;
    }
    return NavigationIndices.create(previousLegIndex, (previousStepIndex + 1));
  }

  static List<Milestone> checkMilestones(RouteProgress previousRouteProgress,
                                         RouteProgress routeProgress,
                                         MapboxNavigation mapboxNavigation) {
    List<Milestone> milestones = new ArrayList<>();
    for (Milestone milestone : mapboxNavigation.getMilestones()) {
      if (milestone.isOccurring(previousRouteProgress, routeProgress)) {
        milestones.add(milestone);
      }
    }
    return milestones;
  }

  static boolean isUserOffRoute(NewLocationModel newLocationModel, RouteProgress routeProgress,
                                OffRouteCallback callback) {
    MapboxNavigationOptions options = newLocationModel.mapboxNavigation().options();
    if (!options.enableOffRouteDetection()) {
      return false;
    }
    Location location = newLocationModel.location();
    OffRoute offRoute = newLocationModel.mapboxNavigation().getOffRouteEngine();
    setOffRouteDetectorCallback(offRoute, callback);
    return offRoute.isUserOffRoute(location, routeProgress, options);
  }

  static boolean shouldCheckFasterRoute(NewLocationModel newLocationModel, RouteProgress routeProgress) {
    FasterRoute fasterRoute = newLocationModel.mapboxNavigation().getFasterRouteEngine();
    return fasterRoute.shouldCheckFasterRoute(newLocationModel.location(), routeProgress);
  }

  static Location getSnappedLocation(MapboxNavigation mapboxNavigation, Location location,
                                     RouteProgress routeProgress, List<Point> stepCoordinates) {
    Snap snap = mapboxNavigation.getSnapEngine();
    return snap.getSnappedLocation(location, routeProgress, stepCoordinates);
  }

  /**
   * Retrieves the next steps maneuver position if one exist, otherwise it decodes the current steps
   * geometry and uses the last coordinate in the position list.
   */
  static Point nextManeuverPosition(int stepIndex, List<LegStep> steps, List<Point> coords) {
    // If there is an upcoming step, use it's maneuver as the position.
    if (steps.size() > (stepIndex + 1)) {
      return steps.get(stepIndex + 1).maneuver().location();
    }
    return !coords.isEmpty() ? coords.get(coords.size() - 1) : coords.get(coords.size());
  }

  private static void setOffRouteDetectorCallback(OffRoute offRoute, OffRouteCallback callback) {
    if (offRoute instanceof OffRouteDetector) {
      ((OffRouteDetector) offRoute).setOffRouteCallback(callback);
    }
  }
}
