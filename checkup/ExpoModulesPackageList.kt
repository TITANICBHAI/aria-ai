package expo.modules

import expo.modules.blur.BlurModule
import expo.modules.constants.ConstantsModule
import expo.modules.font.FontLoaderModule
import expo.modules.font.FontUtilsModule
import expo.modules.haptics.HapticsModule
import expo.modules.image.ExpoImageModule
import expo.modules.imagepicker.ImagePickerModule
import expo.modules.lineargradient.LinearGradientModule
import expo.modules.linking.ExpoLinkingModule
import expo.modules.location.LocationModule
import expo.modules.splashscreen.SplashScreenModule
import expo.modules.systemui.SystemUIModule
import expo.modules.webbrowser.WebBrowserModule
import expo.modules.kotlin.ModulesProvider
import expo.modules.kotlin.modules.Module

class ExpoModulesPackageList : ModulesProvider {
    override fun getModulesList(): List<Class<out Module>> = listOf(
        BlurModule::class.java,
        ConstantsModule::class.java,
        FontLoaderModule::class.java,
        FontUtilsModule::class.java,
        HapticsModule::class.java,
        ExpoImageModule::class.java,
        ImagePickerModule::class.java,
        LinearGradientModule::class.java,
        ExpoLinkingModule::class.java,
        LocationModule::class.java,
        SplashScreenModule::class.java,
        SystemUIModule::class.java,
        WebBrowserModule::class.java,
    )
}
