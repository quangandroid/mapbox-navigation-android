package com.mapbox.services.android.navigation.v5.navigation;


import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;

// TODO Check and remove if not necessary
public class TelemetryUtils {
  private static final String MAPBOX_SHARED_PREFERENCES_FILE = "MapboxSharedPreferences";
  static final String MAPBOX_SHARED_PREFERENCE_KEY_VENDOR_ID = "mapboxVendorId";

  public static String buildUuid() {
    return UUID.randomUUID().toString();
  }

  static SharedPreferences getSharedPreferences(Context context) {
    return context.getSharedPreferences(MAPBOX_SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE);
  }
}
