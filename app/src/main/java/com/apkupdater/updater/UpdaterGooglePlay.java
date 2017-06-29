package com.apkupdater.updater;

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

import android.content.Context;

import com.apkupdater.model.InstalledApp;
import com.apkupdater.model.Update;
import com.apkupdater.service.AutomaticInstallerService_;
import com.apkupdater.util.GenericCallback;
import com.apkupdater.util.GooglePlayUtil;
import com.apkupdater.util.ServiceUtil;
import com.github.yeriomin.playstoreapi.BulkDetailsEntry;
import com.github.yeriomin.playstoreapi.BulkDetailsResponse;
import com.github.yeriomin.playstoreapi.DocV2;
import com.github.yeriomin.playstoreapi.GooglePlayAPI;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

public class UpdaterGooglePlay
{
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private static GooglePlayAPI mApi = null;

    private List<InstalledApp> mApps;
    private Context mContext;
    private String mError;
    private UpdaterStatus mResultCode = UpdaterStatus.STATUS_UPDATE_FOUND;
    private List<Update> mUpdates = new ArrayList<>();

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public UpdaterGooglePlay(
        Context context,
        List<InstalledApp> apps,
        ExecutorService executor,
        final GenericCallback<Update> callback
    ) {
        try {
            // Store vars
            mApps = apps;
            mContext = context;
            mApi = GooglePlayUtil.getApi(mContext);

            if (mApi == null) {
                mError = "Unable to get GooglePlayApi";
                mResultCode = UpdaterStatus.STATUS_ERROR;
                return;
            }

            List<String> pnames = new ArrayList<>();
            for (InstalledApp app : apps) {
                pnames.add(app.getPname());
            }

            BulkDetailsResponse response = mApi.bulkDetails(pnames);

            if (response == null || response.getEntryList() == null) {
                mError = "Response is null";
                mResultCode = UpdaterStatus.STATUS_ERROR;
                return;
            }

            final UpdaterOptions options = new UpdaterOptions(context);

            for (BulkDetailsEntry entry : response.getEntryList()) {
                if (!entry.hasDoc()) {
                    callback.onResult(null);
                    continue;
                }

                final DocV2 details = entry.getDoc();
                final int versionCode = details.getDetails().getAppDetails().getVersionCode();
                final String pname = details.getDetails().getAppDetails().getPackageName();
                final InstalledApp app = getInstalledApp(pname);

                if (app == null) {
                    callback.onResult(null);
                    continue;
                }

                if (versionCode > app.getVersionCode()) {
                    executor.execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Update u = new Update(
                                    app,
                                    "",
                                    details.getDetails().getAppDetails().hasVersionString() ? details.getDetails().getAppDetails().getVersionString() : "?",
                                    false,
                                    "Cookie",
                                    versionCode
                                );

                                mUpdates.add(u);

                                if (options.automaticInstall()) {
                                    callback.onResult(null);
                                } else {
                                    callback.onResult(u);
                                }
                            } catch (Exception ex) {
                                callback.onResult(null);
                            }
                        }
                    });
                }
            }

            executor.shutdown();
            while (!executor.isTerminated()) {
                Thread.sleep(1);
            }

            if (options.automaticInstall() && !ServiceUtil.isServiceRunning(context, AutomaticInstallerService_.class)) {
                List<String> l = new ArrayList<>();
                for (Update u : mUpdates) {
                    l.add(u.getPname());
                }

                AutomaticInstallerService_
                    .intent(context.getApplicationContext())
                    .extra("apps", l.toArray(new String[0]))
                    .start();
            }
        } catch (Exception e) {
            mError = String.valueOf(e);
            mResultCode = UpdaterStatus.STATUS_ERROR;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private InstalledApp getInstalledApp(
        String pname
    ) {
        for (InstalledApp app : mApps) {
            if (app.getPname().equals(pname)) {
                return app;
            }
        }
        return null;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public Throwable getResultError(
    ) {
        return new Throwable(mError + " | Source: GooglePlay");
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public UpdaterStatus getResultStatus(
    ) {
        return mResultCode;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public List<Update> getUpdates(
    ) {
        return mUpdates;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////