package com.ysdc.aidpdf.reminder.utils;

import android.os.Build;

import androidx.annotation.NonNull;

import java.util.Locale;

public class ManufacturerUtils {
    private static final String LGE = "lge";
    private static final String SAMSUNG = "samsung";
    private static final String GOOGLE = "google";

    private ManufacturerUtils() {
    }

    /**
     * Returns true if the device manufacturer is LG.
     */
    public static boolean isLGEDevice() {
        return getManufacturer().equals(LGE);
    }

    /**
     * Returns true if the device manufacturer is Samsung.
     */
    public static boolean isSamsungDevice() {
        return getManufacturer().equals(SAMSUNG);
    }

    public static boolean isGoogleDevice() {
        return getManufacturer().equals(GOOGLE);
    }

    /**
     * Returns true if the date input keyboard is potentially missing separator characters such as /.
     */
    public static boolean isDateInputKeyboardMissingSeparatorCharacters() {
        return isLGEDevice() || isSamsungDevice();
    }

    @NonNull
    private static String getManufacturer() {
        final String manufacturer = Build.MANUFACTURER;
        if (manufacturer != null) {
            return manufacturer.toLowerCase(Locale.ENGLISH);
        } else {
            return "";
        }
    }
    public static boolean isXiaomi() {
        String manufacturer = Build.MANUFACTURER;
        String brand = Build.BRAND;
        return manufacturer.equalsIgnoreCase("xiaomi")
                || brand.equalsIgnoreCase("xiaomi")
                || brand.equalsIgnoreCase("redmi")
                || brand.equalsIgnoreCase("poco");
    }
    public static boolean isOneNoticeDevice() {
        String manufacturer = Build.MANUFACTURER;
        String brand = Build.BRAND;
        return isXiaomi()
                || manufacturer.equalsIgnoreCase("FCNT")
                || manufacturer.equalsIgnoreCase("SHARP")
                || (brand.equalsIgnoreCase("google") && manufacturer.equalsIgnoreCase("google"));
    }
    public static boolean isAndroid16AndAbove() {
        return Build.VERSION.SDK_INT >= 36;
    }
    public static boolean isAndroid12AndAbove() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }
    public static boolean isOnePlus() {
        String manufacturer = Build.MANUFACTURER;
        String brand = Build.BRAND;
        return manufacturer.equalsIgnoreCase("oneplus")
                || brand.equalsIgnoreCase("oneplus");
    }
}
