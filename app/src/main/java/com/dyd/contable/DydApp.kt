package com.dyd.contable

import android.app.Application
import com.dyd.contable.data.AccountingRepository
import com.dyd.contable.data.remote.Supabase

class DydApp : Application() {
    val repository: AccountingRepository by lazy { AccountingRepository(Supabase.create()) }
}
