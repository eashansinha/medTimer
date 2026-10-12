package com.futsch1.medtimer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Device checks for the Android 16/17 behavior changes that need a running system UI:
 * IMP-01 (edge-to-edge enforcement: content must not be drawn under the system bars) and
 * IMP-02/IMP-27 (back dispatch through the AndroidX OnBackPressedDispatcher).
 * <p>
 * The checks run on the main thread via Instrumentation#runOnMainSync instead of Espresso
 * view interactions, so they do not depend on the app's debug-only looper idling resources.
 */
public class Android17BehaviorTest extends BaseTestHelper {

    private interface ActivityAction {
        void run(MainActivity activity);
    }

    /** Runs the action on the main thread against the launched activity (no Espresso idling involved). */
    private void onActivity(ActivityAction action) {
        MainActivity activity = baristaRule.getActivityTestRule().getActivity();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> action.run(activity));
    }

    private void waitForLayout() throws InterruptedException {
        AtomicReference<View> toolbar = new AtomicReference<>();
        for (int i = 0; i < 50 && (toolbar.get() == null || !toolbar.get().isLaidOut()); i++) {
            onActivity(activity -> toolbar.set(activity.findViewById(R.id.toolbar)));
            Thread.sleep(100);
        }
        assertNotNull("Toolbar never laid out", toolbar.get());
        assertTrue(toolbar.get().isLaidOut());
    }

    @Test
    public void edgeToEdgeInsetsKeepToolbarAndBottomNavigationVisible() throws InterruptedException {
        waitForLayout();
        AtomicReference<String> failure = new AtomicReference<>();
        onActivity(activity -> {
            View toolbar = activity.findViewById(R.id.toolbar);
            View bottomNavigation = activity.findViewById(R.id.bottomNavigationView);
            View root = toolbar.getRootView();
            WindowInsetsCompat rootInsets = ViewCompat.getRootWindowInsets(root);
            if (rootInsets == null) {
                failure.set("No root window insets");
                return;
            }
            Insets systemBars = rootInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            int[] toolbarLocation = new int[2];
            toolbar.getLocationOnScreen(toolbarLocation);
            if (toolbarLocation[1] < systemBars.top) {
                failure.set("Toolbar top " + toolbarLocation[1] + " is above the status bar inset " + systemBars.top);
                return;
            }
            // Under edge-to-edge the root view spans the whole screen, so the navigation bar occupies
            // the bottom systemBars.bottom pixels of the root view.
            int[] bottomNavigationLocation = new int[2];
            bottomNavigation.getLocationOnScreen(bottomNavigationLocation);
            int bottomNavigationBottom = bottomNavigationLocation[1] + bottomNavigation.getHeight();
            int[] rootLocation = new int[2];
            root.getLocationOnScreen(rootLocation);
            int navigationBarTop = rootLocation[1] + root.getHeight() - systemBars.bottom;
            if (bottomNavigationBottom > navigationBarTop) {
                failure.set("Bottom navigation bottom " + bottomNavigationBottom + " overlaps the navigation bar starting at "
                        + navigationBarTop + " (inset " + systemBars.bottom + ")");
            }
        });
        assertEquals(null, failure.get());
    }

    @Test
    public void systemBackReturnsToOverview() throws InterruptedException {
        waitForLayout();
        onActivity(activity -> {
            BottomNavigationView bottomNavigation = activity.findViewById(R.id.bottomNavigationView);
            bottomNavigation.setSelectedItemId(R.id.medicinesFragment);
        });
        assertEquals(R.id.medicinesFragment, waitForSelectedItem(R.id.medicinesFragment));

        onActivity(activity -> activity.getOnBackPressedDispatcher().onBackPressed());

        assertEquals(R.id.overviewFragment, waitForSelectedItem(R.id.overviewFragment));
    }

    private int waitForSelectedItem(int expectedId) throws InterruptedException {
        AtomicInteger selected = new AtomicInteger(0);
        for (int i = 0; i < 50 && selected.get() != expectedId; i++) {
            Thread.sleep(100);
            onActivity(activity -> {
                BottomNavigationView bottomNavigation = activity.findViewById(R.id.bottomNavigationView);
                selected.set(bottomNavigation.getSelectedItemId());
            });
        }
        return selected.get();
    }
}
