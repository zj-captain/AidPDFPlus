package androidx.core.app;

import android.util.Log;

import com.ysdc.aidpdf.BuildConfig;

public abstract class BJobIntentService extends JobIntentService {

    public void teA() {
        if (BuildConfig.DEBUG) {
            Log.e("BJobIntentService", "teA");
        }
    }

    @Override
    GenericWorkItem dequeueWork() {
        try {
            teA();
            return super.dequeueWork();
        } catch (Exception ignored) {
            return null;
        }
    }
}
