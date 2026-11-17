// Standalone spike project (P4 U-08): not part of the root build. Plugins are declared here, applied in :app.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.roborazzi) apply false
}
