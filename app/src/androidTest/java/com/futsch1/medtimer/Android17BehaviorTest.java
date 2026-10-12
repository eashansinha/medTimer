package com.futsch1.medtimer;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Rect;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.junit.Test;

/**
 * Device checks for the Android 16/17 behavior changes that need a running system UI:
 * IMP-01 (edge-to-edge enforcement: content must not be drawn under the system bars) and
 * IMP-02/IMP-27 (predictive back dispatch through the AndroidX OnBackPressedDispatcher).
 */
public class Android17BehaviorTest extends BaseTestHelper {

    @Test
    public void edgeToEdgeInsetsKeepToolbarAndBottomNavigationVisible() {
        onView(withId(R.id.toolbar)).check((view, noViewFoundException) -> {
            assertNotNull(noViewFoundException == null ? view : null);
            View root = view.getRootView();
            WindowInsetsCompat rootInsets = ViewCompat.getRootWindowInsets(root);
            assertNotNull("No root window insets", rootInsets);
            Insets systemBars = rootInsets.getInsets(WindowInsetsCompat.Type.systemBars());

            int[] toolbarLocation = new int[2];
            view.getLocationOnScreen(toolbarLocation);
            assertTrue("Toolbar top " + toolbarLocation[1] + " is above the status bar inset " + systemBars.top,
                    toolbarLocation[1] >= systemBars.top);

            View bottomNavigation = root.findViewById(R.id.bottomNavigationView);
            assertNotNull(bottomNavigation);
            Rect visible = new Rect();
            assertTrue(bottomNavigation.getGlobalVisibleRect(visible));
            Rect windowVisible = new Rect();
            root.getWindowVisibleDisplayFrame(windowVisible);
            assertTrue("Bottom navigation bottom " + visible.bottom + " overlaps the navigation bar inset " + systemBars.bottom
                            + " (window bottom " + windowVisible.bottom + ")",
                    visible.bottom <= windowVisible.bottom - systemBars.bottom || systemBars.bottom == 0);
        });
    }

    @Test
    public void systemBackReturnsToOverview() {
        AndroidTestHelper.navigateTo(AndroidTestHelper.MainMenu.MEDICINES);
        onView(withId(R.id.bottomNavigationView)).check((view, noViewFoundException) ->
                assertEquals(R.id.medicinesFragment, ((BottomNavigationView) view).getSelectedItemId()));

        pressBack();

        onView(withId(R.id.bottomNavigationView)).check((view, noViewFoundException) ->
                assertEquals(R.id.overviewFragment, ((BottomNavigationView) view).getSelectedItemId()));
    }
}
