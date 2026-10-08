package com.retally.app.ui.main

import com.retally.shared.core.ViewModel
import com.retally.shared.domain.ads.AdsConfig
import com.retally.shared.presentation.main.MainContract.Action
import com.retally.shared.presentation.main.MainContract.SideEffect
import com.retally.shared.presentation.main.MainContract.State
import com.retally.shared.presentation.main.MainContract.Store
import com.retally.shared.presentation.main.MainTab

/**
 * Activity-scoped shell ViewModel: outlives the tab fragments so ad eligibility (decided once
 * on the splash) and the selected tab survive navigation and rotation.
 */
class MainViewModel(
    override val store: Store,
    private val adsConfig: AdsConfig,
) : ViewModel<State, Action, SideEffect>() {

    val homeBannerUnitId: String get() = adsConfig.homeBannerUnitId

    /** Called by the splash once the ads SDK reported readiness. */
    fun onAdsInitialized(adsReady: Boolean) =
        dispatch(Action.Init(adsEnabled = adsReady && adsConfig.enabled))

    fun selectTab(tab: MainTab) = dispatch(Action.SelectTab(tab))
}
