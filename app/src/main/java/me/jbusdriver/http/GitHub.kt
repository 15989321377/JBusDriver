package me.jbusdriver.http

import io.reactivex.Flowable
import me.jbusdriver.BuildConfig
import me.jbusdriver.library.http.NetClient
import retrofit2.http.GET
import retrofit2.http.Path


/**
 * Created by Administrator on 2017/4/15 0015.
 */
interface GitHub {
    @GET("https://raw.githubusercontent.com/Ccixyj/JBusDriver/{branch}/api/announce.json")
    fun announce(@Path("branch") branch: String = if (BuildConfig.DEBUG) "dev" else "master"): Flowable<String>

//
//    @GET("https://raw.githubusercontent.com/Ccixyj/JBusDriver/plugin/api/announce.json")
//    fun announceWithPlugin(): Flowable<String>


    companion object {
        val INSTANCE by lazy { NetClient.getRetrofit("https://raw.githubusercontent.com/").create(GitHub::class.java) }
    }
}