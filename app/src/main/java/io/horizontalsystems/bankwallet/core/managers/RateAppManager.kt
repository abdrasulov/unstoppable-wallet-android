package io.horizontalsystems.bankwallet.core.managers

import android.os.Handler
import io.horizontalsystems.bankwallet.core.IAdapterManager
import io.horizontalsystems.bankwallet.core.ILocalStorage
import io.horizontalsystems.bankwallet.core.IRateAppManager
import io.horizontalsystems.bankwallet.core.IWalletManager
import io.reactivex.subjects.PublishSubject
import java.math.BigDecimal
import java.time.Instant


class RateAppManager(
        private val walletManager: IWalletManager,
        private val adapterManager: IAdapterManager,
        private val localStorage: ILocalStorage) : IRateAppManager {

    override val showRateAppObservable = PublishSubject.create<Unit>()

    private val MIN_LAUNCH_COUNT = 5
    private val MIN_COINS_COUNT = 2
    private val COUNTDOWN_TIME_INTERVAL: Long = 10 * 1000 // 10 seconds
    private val REQUEST_TIME_INTERVAL = 90 * 24 * 60 * 60 // 90 Days

    private var isCountdownAllowed = false
    private var isCountdownPassed = false
    private var isRequestAllowed = false
    private var isOnBalancePage = false

    private fun onCountdownPass() {

        var balance: BigDecimal = BigDecimal.ZERO

        for (wallet in walletManager.wallets) {
            val adapter = adapterManager.getBalanceAdapterForWallet(wallet)
            adapter?.let {
                balance = it.balance
            }

            if (balance > BigDecimal.ZERO)
                break
        }

        if (walletManager.wallets.size >= MIN_COINS_COUNT && balance > BigDecimal.ZERO) {
            isRequestAllowed = true
            showIfAllowed()
        }
    }

    private fun showIfAllowed() {
        if (isOnBalancePage && isRequestAllowed) {
            localStorage.rateAppLastRequestTime = Instant.now().epochSecond
            isRequestAllowed = false

            showRateAppObservable.onNext(Unit)
        }
    }

    override fun onBalancePageActive() {
        isOnBalancePage = true
        showIfAllowed()
    }

    override fun onBalancePageInactive() {
        isOnBalancePage = false
    }

    override fun onAppLaunch() {

        val launchCount = localStorage.appLaunchCount
        if (launchCount < MIN_LAUNCH_COUNT) {
            localStorage.appLaunchCount = launchCount + 1
            return
        }

        val lastRequestTime = localStorage.rateAppLastRequestTime
        if ((Instant.now().epochSecond - lastRequestTime) < REQUEST_TIME_INTERVAL) {
            return
        }

        isCountdownAllowed = true
    }

    override fun onAppBecomeActive() {

        if(isCountdownAllowed && !isCountdownPassed){
            startCountdownChecker()
        }
    }

    private fun startCountdownChecker() {
        val handler = Handler()
        handler.postDelayed(
                {
                    isCountdownPassed = true
                    onCountdownPass()
                },
                COUNTDOWN_TIME_INTERVAL)
    }

}