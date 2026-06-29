@file:Suppress("MatchingDeclarationName")

package com.campuscue.ui.settings

internal data class ChangelogEntry(
    val version: String,
    val code: Int,
    val date: String,
    val title: String,
    val highlights: List<String>,
)

internal val APP_CHANGELOG =
    listOf(
        ChangelogEntry(
            version = "1.6.7",
            code = 43,
            date = "2026-06-28",
            title = "Cleanup & Polish",
            highlights =
                listOf(
                    "Removed the GPA Planner tab — Grades now focuses on Performance and Results",
                    "Leaner, faster app from an internal code cleanup",
                    "Hardened logging so personal data stays off the device logs",
                ),
        ),
        ChangelogEntry(
            version = "1.6.6",
            code = 42,
            date = "2026-06-12",
            title = "GPA Planner Rebuilt & Smarter Loading",
            highlights =
                listOf(
                    "GPA Planner now knows course types — theory, lab, and theory+lab with a pane switcher",
                    "Real credit-weighted SGPA with per-course credit control",
                    "Lab marks use GU's official structure (Work 15, Record 10, Test 25, Exam 50)",
                    "Your planner inputs now survive app restarts",
                    "Animated loading screen replaces blank waits",
                    "Grades tabs stay usable even when one tab hits a server error",
                    "Friendlier error messages — no more raw server text",
                    "Update notifications and class reminders now work in the background",
                    "Light mode highlights now use your accent color instead of grey",
                ),
        ),
        ChangelogEntry(
            version = "1.6.5",
            code = 41,
            date = "2026-06-10",
            title = "Consistency & Cleanup",
            highlights =
                listOf(
                    "Offline indicator added to Day-wise",
                    "Attendance tab opens faster — Day-wise no longer refreshes in the background",
                    "Content no longer hides behind the nav bar on 3-button navigation",
                    "Long course names no longer stretch cards — capped with ellipsis",
                    "Status badges look identical across Attendance and Timetable",
                    "Smoother calendar swipes in Day-wise",
                ),
        ),
        ChangelogEntry(
            version = "1.6.4",
            code = 40,
            date = "2026-06-10",
            title = "Faster Start & Smoother Feel",
            highlights =
                listOf(
                    "Instant cold start — splash screen replaces the white flash",
                    "Subtle touch feedback restored on all buttons and cards",
                    "System theme mode follows your device dark/light setting",
                    "Dynamic color on Android 12+ matches your wallpaper",
                    "Dark cards and nav bar now match your color profile",
                    "Dashboard schedule LIVE indicator updates in real time",
                    "Light haptics on QR scan, tab swipes, and planner long-press",
                ),
        ),
        ChangelogEntry(
            version = "1.6.3",
            code = 39,
            date = "2026-06-09",
            title = "UI Polish & Code Cleanup",
            highlights =
                listOf(
                    "Fixed grey card highlights in light mode",
                    "Minimal, icon-free Settings page",
                    "Cleaned up dead code and lint warnings",
                ),
        ),
        ChangelogEntry(
            version = "1.6.2",
            code = 38,
            date = "2026-06-09",
            title = "Admin Dashboard & Auth Hardening",
            highlights =
                listOf(
                    "Web admin dashboard for user management",
                    "Ban/unban users with dedicated blocked screen",
                    "Force-reauth fail-closed on network errors",
                    "Cleaner login method toggle",
                    "Removed guided tour",
                ),
        ),
        ChangelogEntry(
            version = "1.6.0",
            code = 36,
            date = "2026-06-09",
            title = "Grades Overhaul & GPA Planner Fixes",
            highlights =
                listOf(
                    "Grades screen restructured into focused components for better performance",
                    "GPA Planner: toggle which exams a course has (IA1/IA2/MTE/ETE)",
                    "Sleek minimal sliders replace bulky Material defaults in GPA tab",
                    "Tab highlight now syncs smoothly with horizontal pager swipe",
                    "Update history viewable from About CampusCue in Settings",
                ),
        ),
        ChangelogEntry(
            version = "1.5.9",
            code = 35,
            date = "2026-06-07",
            title = "Grades & Marks Screen",
            highlights =
                listOf(
                    "New Grades screen with Results, Performance, and GPA Planner tabs",
                    "View report cards and grade sheets inline",
                    "Performance marks breakdown by exam component",
                    "Marks insights with bar chart visualization",
                ),
        ),
        ChangelogEntry(
            version = "1.5.8",
            code = 34,
            date = "2026-06-05",
            title = "QR Widget, Next Class & Error Handling",
            highlights =
                listOf(
                    "Home-screen QR scan widget for one-tap attendance",
                    "App shortcut for quick QR scan access",
                    "Dashboard \"Next class\" countdown with 30s ticker",
                    "Typed error handling across all screens (server down, offline, session expired)",
                    "Smart retry with exponential backoff for server errors",
                    "Tolerant semester loading — partial failures no longer break the whole flow",
                ),
        ),
        ChangelogEntry(
            version = "1.5.7",
            code = 33,
            date = "2026-06-02",
            title = "UI Polish & Accessibility",
            highlights =
                listOf(
                    "Offline banner added to Timetable and Planner screens",
                    "Text overflow handling — long subject names now ellipsize properly",
                    "Theme-safe colors — substitution badges adapt to your color profile",
                    "Accessibility review — all icon controls labeled for TalkBack",
                ),
        ),
        ChangelogEntry(
            version = "1.5.6",
            code = 32,
            date = "2026-05-30",
            title = "Speed Improvements",
            highlights =
                listOf(
                    "Dashboard, Attendance, and Planner now fetch data concurrently",
                    "Semester options load in parallel across academic years",
                    "Token refresh is mutex-guarded to prevent double-refresh logouts",
                    "Timetable uses stale-while-revalidate — shows cached schedule instantly",
                    "Theme changes no longer block the UI thread",
                ),
        ),
        ChangelogEntry(
            version = "1.5.5",
            code = 31,
            date = "2026-05-27",
            title = "QR Scan Refinements",
            highlights =
                listOf(
                    "Faster QR success animation with opaque background",
                    "Removed post-scan attendance refresh (attendance updates when teacher submits)",
                    "Cleaner confirmation text instead of raw server JSON",
                ),
        ),
        ChangelogEntry(
            version = "1.5.4",
            code = 30,
            date = "2026-05-24",
            title = "Planner & QR Enhancements",
            highlights =
                listOf(
                    "Recovery forecast moved to Planner",
                    "Tap a date to preview attendance, long-press to mark absent",
                    "Day-wise auto-refresh on open with last-updated indicator",
                    "Digital zoom (up to 10x) in QR scanner for distant codes",
                    "QR success overlay z-order fix",
                ),
        ),
        ChangelogEntry(
            version = "1.5.0",
            code = 25,
            date = "2026-05-15",
            title = "Planner Reliability & Login Redesign",
            highlights =
                listOf(
                    "Planner uses date-keyed timetable data (holidays respected)",
                    "Combined PP+PR subjects show correct projected percentages",
                    "4-week timetable cache warmed on Planner load",
                    "GU-only polished phone OTP login with launcher logo",
                    "Compact color profile chips in Settings",
                    "Private APK self-updater fully wired",
                ),
        ),
        ChangelogEntry(
            version = "1.4.0",
            code = 20,
            date = "2026-05-08",
            title = "Performance & Offline Resilience",
            highlights =
                listOf(
                    "Baseline Profiles for faster app startup",
                    "Heavy computation moved off the main thread",
                    "Spring-based predictive back animations",
                    "Shimmer skeleton loading placeholders",
                    "Offline-resilient caching — cached data shown when network fails",
                    "Offline banner in Dashboard and Attendance",
                ),
        ),
        ChangelogEntry(
            version = "1.3.0",
            code = 15,
            date = "2026-05-01",
            title = "90Hz Scroll Fix & Animations",
            highlights =
                listOf(
                    "Compose stability config eliminates scroll jank at 90Hz+",
                    "Smooth item entrance/exit animations in all lists",
                    "Proper view recycling with contentType on all LazyColumn items",
                ),
        ),
        ChangelogEntry(
            version = "1.2.0",
            code = 10,
            date = "2026-04-22",
            title = "Expo-Matching UI Rewrite",
            highlights =
                listOf(
                    "All screens rewritten to match the Expo reference design",
                    "Flat cards with 1px borders, zero elevation",
                    "Inter font family with tuned typography",
                    "5 color profiles: Iris, Forest, Slate, Amber, Crimson",
                    "Monospace font for codes, times, and percentages",
                ),
        ),
        ChangelogEntry(
            version = "1.0.0",
            code = 1,
            date = "2026-04-10",
            title = "Initial Release",
            highlights =
                listOf(
                    "Dashboard with attendance overview and subject cards",
                    "Subject-wise attendance with bunk/need badges",
                    "Weekly timetable with live class progress",
                    "Day-wise attendance calendar",
                    "Planner with leave budget and leave simulator",
                    "Settings with theme, threshold, and export options",
                    "Biometric gate and secure token storage",
                ),
        ),
    )
