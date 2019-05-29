package me.jbusdriver.ui.activity

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.Toolbar
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.format.DateUtils
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.View
import com.afollestad.materialdialogs.MaterialDialog
import com.billy.cc.core.component.CC
import com.chad.library.adapter.base.entity.MultiItemEntity
import com.umeng.analytics.MobclickAgent
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.activity_setting.*
import kotlinx.android.synthetic.main.layout_collect_back_edit_item.view.*
import kotlinx.android.synthetic.main.layout_menu_op_item.view.*
import me.jbusdriver.R
import me.jbusdriver.base.*
import me.jbusdriver.base.common.BaseActivity
import me.jbusdriver.base.common.C
import me.jbusdriver.common.JBus
import me.jbusdriver.common.bean.plugin.PluginBean
import me.jbusdriver.db.service.LinkService
import me.jbusdriver.mvp.bean.BackUpEvent
import me.jbusdriver.mvp.bean.Expand_Type_Head
import me.jbusdriver.mvp.bean.MenuOp
import me.jbusdriver.mvp.bean.MenuOpHead
import me.jbusdriver.ui.adapter.MenuOpAdapter
import me.jbusdriver.ui.data.AppConfiguration
import me.jbusdriver.ui.task.LoadCollectService
import java.io.File
import java.util.concurrent.TimeUnit

class SettingActivity : BaseActivity() {

    private var pageModeHolder = AppConfiguration.pageMode
    private val menuOpValue by lazy { AppConfiguration.menuConfig.toMutableMap() }


    private val backDir by lazy {
        val pathSuffix = File.separator + "collect" + File.separator + "backup" + File.separator
        val dir: String =
            createDir(Environment.getExternalStorageDirectory().absolutePath + File.separator + JBus.packageName + pathSuffix)
                ?: createDir(JBus.filesDir.absolutePath + pathSuffix)
                ?: error("cant not create collect dir in anywhere")
        File(dir)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setting)
        setToolBar()
        initSettingView()
        RxBus.toFlowable(BackUpEvent::class.java).throttleLast(100, TimeUnit.MILLISECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                tv_collect_backup.text = "正在加载备份${it.path}的第${it.index}/${it.total}个"
                if (it.total == it.index) {
                    tv_collect_backup.text = "点击备份"
                    tv_collect_backup.isClickable = it.total == it.index
                }

            }, {

                tv_collect_backup.text = "点击备份"
                tv_collect_backup.isClickable = true
            }).addTo(rxManager)
    }

    @SuppressLint("ResourceAsColor")
    private fun initSettingView() {

        //check install plugins
        ll_check_plugins.setOnClickListener {

            val plugins = CC.obtainBuilder(C.Components.PluginManager)
                .setActionName("plugins.info")
                .build()
                .call()
                .getDataItemWithNoKey<List<PluginBean>>()
            val pluginInfos = plugins?.map { "${it.name} : ${it.versionName}" } ?: emptyList()
            MaterialDialog.Builder(this)
                .title(if (plugins.isNullOrEmpty()) "没有插件信息!" else "已安装插件")
                .items(pluginInfos)
                .show()
        }


        //page mode
        changePageMode(AppConfiguration.pageMode)
        ll_page_mode_page.setOnClickListener {
            pageModeHolder = AppConfiguration.PageMode.Page
            changePageMode(AppConfiguration.PageMode.Page)
        }
        ll_page_mode_normal.setOnClickListener {
            pageModeHolder = AppConfiguration.PageMode.Normal
            changePageMode(AppConfiguration.PageMode.Normal)
        }

        //menu op
        val data: List<MultiItemEntity> = arrayListOf(
            MenuOpHead("个人").apply { MenuOp.mine.forEach { addSubItem(it) } },
            MenuOpHead("有碼").apply { MenuOp.nav_ma.forEach { addSubItem(it) } },
            MenuOpHead("無碼").apply { MenuOp.nav_uncensore.forEach { addSubItem(it) } },
            MenuOpHead("欧美").apply { MenuOp.nav_xyz.forEach { addSubItem(it) } },
            MenuOpHead("其他").apply { MenuOp.nav_other.forEach { addSubItem(it) } }
        )
        val adapter = MenuOpAdapter(data)
        adapter.bindToRecyclerView(rv_menu_op)
        rv_menu_op.layoutManager = GridLayoutManager(viewContext, viewContext.spanCount).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int) =
                    if (adapter.getItemViewType(position) == Expand_Type_Head) spanCount else 1
            }
        }
        //有选项选中就展开
        val expandItems = data.filterIndexed { _, multiItemEntity ->
            multiItemEntity is MenuOpHead && multiItemEntity.subItems.any { it.isHow }
        }
        expandItems.forEach {
            adapter.expand(data.indexOf(it))
        }
        adapter.setOnItemClickListener { _, view, position ->
            (adapter.data.getOrNull(position) as? MenuOp)?.let {
                view.cb_nav_menu?.let { cb ->
                    //添加设置
                    synchronized(cb) {
                        cb.isChecked = !cb.isChecked
                        menuOpValue[it.name] = cb.isChecked
                    }
                }
            }

        }

        //magnet source

        loadMagNetConfig()


        //收藏分类
        sw_collect_category.isChecked = AppConfiguration.enableCategory
        sw_collect_category.setOnCheckedChangeListener { _, isChecked ->
            AppConfiguration.enableCategory = isChecked
        }

        //备份
        tv_collect_backup.setOnClickListener {

            val loading = MaterialDialog.Builder(viewContext).content("正在备份...").progress(true, 0).show()
            Flowable.fromCallable { backDir }
                .flatMap { file ->
                    return@flatMap LinkService.queryAll().doOnNext {
                        File(file, "backup${System.currentTimeMillis()}.json").writeText(it.toJsonString())
                    }
                }.compose(SchedulersCompat.single())
                .doAfterTerminate { loading.dismiss() }
                .subscribeBy(onError = { toast("备份失败,请重新打开app") }, onNext = {
                    toast("备份成功")
                    loadBackUp()
                })
                .addTo(rxManager)
        }

        loadBackUp()

    }

    private fun loadMagNetConfig() {

        CC.obtainBuilder(C.Components.Magnet)
            .setActionName("allKeys")
            .setTimeout(3000L)
            .build().callAsyncCallbackOnMainThread { cc, result ->
                val allMagnetKeys = if (result.isSuccess) {
                    result.getDataItem<List<String>>("keys")
                } else emptyList()


                val configKeyRes = CC.obtainBuilder(C.Components.Magnet)
                    .setActionName("config.getKeys")
                    .build().call()
                val configMagnetKeys = if (configKeyRes.isSuccess) {
                    configKeyRes.getDataItem<List<String>>("keys")
                } else emptyList()

                //if get config failed
                if (!result.isSuccess || !configKeyRes.isSuccess) {
                    tv_magnet_source.text = "初始化配置失败"
                    MobclickAgent.reportError(this, result.toString())
                    return@callAsyncCallbackOnMainThread
                }

                tv_magnet_source.text = configMagnetKeys.joinToString(separator = "   ")

                //init view
                val selectCopy = configMagnetKeys.toMutableList()
                ll_magnet_source_config.setOnClickListener {

                    val selectedIndices = selectCopy.map { allMagnetKeys.indexOf(it) }.toTypedArray()

                    val disables = if (selectedIndices.size <= 1) selectedIndices else emptyArray()

                    MaterialDialog.Builder(viewContext).title("磁力源配置")
                        .items(allMagnetKeys)
                        .itemsCallbackMultiChoice(selectedIndices) { dialog, which, _ ->
                            if (which.size <= 1) {
                                dialog.builder.itemsDisabledIndices(*which)
                            } else {
                                dialog.builder.itemsDisabledIndices()
                            }
                            dialog.notifyItemsChanged()


                            return@itemsCallbackMultiChoice true
                        }.alwaysCallMultiChoiceCallback()
                        .itemsDisabledIndices(*disables)
                        .dismissListener {
                            //保存
                            if (it is MaterialDialog) {
                                //加入配置项
                                val selected = it.selectedIndices?.mapNotNull { allMagnetKeys.toList().getOrNull(it) }
                                    ?: emptyList()
                                selectCopy.clear()
                                selectCopy.addAll(selected)
                                CC.obtainBuilder(C.Components.Magnet)
                                    .setActionName("config.save")
                                    .addParam("keys", selectCopy)
                                    .build().callAsync()

                                tv_magnet_source.text = selected.joinToString(separator = "   ")
                            }

                        }
                        .show()

                }
            }


    }


    private fun secondSpannableString(str: String): SpannableString {
        return SpannableString(str).apply {
            setSpan(
                RelativeSizeSpan(0.8f),
                0,
                str.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            setSpan(
                ForegroundColorSpan(R.color.secondText.toColorInt()),
                0,
                str.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    private fun showBackupFileInfo(file: File) {
        val tip = "拷贝备份目录或备份文件至其他手机的相同sd目录即可。如sdcard/me.jbusdriver/collect/backup/xxx.json"

        val str = SpannableStringBuilder("1. 路径")
            .append(System.getProperty("line.separator"))
            .append(secondSpannableString(file.absolutePath))
            .append(System.getProperty("line.separator"))
            .append(System.getProperty("line.separator"))
            .append("2. 迁移至其他手机")
            .append(System.getProperty("line.separator"))
            .append(secondSpannableString(tip))



        MaterialDialog.Builder(viewContext)
            .title("信息")
            .content(str)
            .show()
    }


    private fun loadBackUp() {


        ll_collect_backup_files.removeAllViews()
        Flowable.fromCallable { backDir }
            .map {
                val list = it.walk().maxDepth(1).filter {
                    it.isFile && it.name.contains("backup.+json".toRegex())
                }.toList()
                if (list.isEmpty()) {
                    listOf(inflate(R.layout.layout_collect_back_edit_item).apply {
                        tv_backup_name.text = "没有备份呢~~"
                        iv_backup_load.visibility = View.GONE
                        iv_backup_delete.visibility = View.GONE
                        iv_backup_info.setOnClickListener {
                            MaterialDialog.Builder(viewContext)
                                .title("信息")
                                .content("还没有备份哦？来一发试试！")
                                .show()
                        }
                    })
                } else {
                    list.mapIndexed { index, file ->
                        inflate(R.layout.layout_collect_back_edit_item).apply {

                            val date = DateUtils.formatDateTime(
                                viewContext, file.lastModified(),
                                DateUtils.FORMAT_SHOW_YEAR or
                                        DateUtils.FORMAT_SHOW_DATE or
                                        DateUtils.FORMAT_SHOW_TIME
                            )



                            tv_backup_name.setOnClickListener {
                                showBackupFileInfo(file)
                            }
                            iv_backup_info.setOnClickListener {
                                showBackupFileInfo(file)
                            }

                            tv_backup_name.text = SpannableStringBuilder("${index + 1}. ${file.name}")
                                .append(System.getProperty("line.separator"))
                                .append("    ")
                                .append(secondSpannableString(date))


                            iv_backup_load.setOnClickListener {
                                MaterialDialog.Builder(viewContext)
                                    .title("加载备份")
                                    .content("${file.name}\n注意:相同文件会被覆盖")
                                    .positiveText("确定")
                                    .negativeText("取消")
                                    .negativeColor(R.color.secondText.toColorInt())
                                    .onPositive { _, _ ->
                                        LoadCollectService.startLoadBackUp(viewContext, file)
                                    }
                                    .show()

                            }
                            iv_backup_delete.setOnClickListener {
                                MaterialDialog.Builder(viewContext)
                                    .title("注意")
                                    .content("确定要删除${file.name}吗?")
                                    .positiveText("确定")
                                    .negativeText("取消")
                                    .negativeColor(R.color.secondText.toColorInt())
                                    .onPositive { _, _ ->
                                        file.deleteRecursively()
                                        loadBackUp()
                                    }
                                    .show()
                            }
                        }
                    }
                }

            }.compose(SchedulersCompat.single())
            .subscribeBy {
                it.forEach {
                    ll_collect_backup_files.addView(it)
                }
            }
            .addTo(rxManager)
    }

    private fun changePageMode(mode: Int) {
        when (mode) {
            AppConfiguration.PageMode.Page -> {
                ll_page_mode_page.setBackgroundResource(R.drawable.mode_page_shape_corner)
                ll_page_mode_normal.setBackgroundResource(0)
            }
            AppConfiguration.PageMode.Normal -> {
                ll_page_mode_page.setBackgroundResource(0)
                ll_page_mode_normal.setBackgroundResource(R.drawable.mode_page_shape_corner)
            }
        }
    }

    private fun setToolBar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "设置"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onStop() {
        super.onStop()
        AppConfiguration.pageMode = pageModeHolder
        if (AppConfiguration.menuConfig != menuOpValue) AppConfiguration.saveSaveMenuConfig(menuOpValue) //必须调用equals
    }

    companion object {
        fun start(context: Context) = context.startActivity(Intent(context, SettingActivity::class.java))
    }
}
