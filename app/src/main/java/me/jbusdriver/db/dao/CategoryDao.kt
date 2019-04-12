package me.jbusdriver.db.dao

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.squareup.sqlbrite3.BriteDatabase
import me.jbusdriver.base.KLog
import me.jbusdriver.base.getIntByColumn
import me.jbusdriver.base.getStringByColumn
import me.jbusdriver.common.bean.db.Category
import me.jbusdriver.common.bean.db.CategoryTable
import java.util.concurrent.TimeUnit

/**
 * Created by Administrator on 2017/9/18 0018.
 */
class CategoryDao(private val db: BriteDatabase) {


    fun insert(category: Category) = try {
        ioBlock {
            val rid = db.insert(CategoryTable.TABLE_NAME, SQLiteDatabase.CONFLICT_IGNORE, category.cv())
            require(rid > 0)
            update(category.copy(tree = "${category.tree}$rid/").apply { this.id = rid.toInt() })
            rid
        }
    } catch (e: Exception) {
        -1L
    }

    @Throws
    fun delete(category: Category) {
        runCatching {
            ioBlock { db.delete(CategoryTable.TABLE_NAME, "${CategoryTable.COLUMN_ID} = ? ", category.id.toString()) }
        }
    }

    fun findById(cId: Int): Category? {
        return kotlin.runCatching {
            db.query(
                "select * from ${CategoryTable.TABLE_NAME}  where ${CategoryTable.COLUMN_ID} = ?",
                cId.toString()
            )?.let {
                KLog.d("cursor :$it")
                if (it.moveToFirst()) {
                    return Category(
                        it.getStringByColumn(CategoryTable.COLUMN_NAME)
                            ?: "", it.getIntByColumn(CategoryTable.COLUMN_P_ID),
                        it.getStringByColumn(CategoryTable.COLUMN_TREE) ?: ""
                    ).apply {
                        id = it.getIntByColumn(CategoryTable.COLUMN_ID)
                    }

                }
                null
            }
        }.getOrNull()
    }

    private fun toCategory(it: Cursor): Category {
        return Category(
            it.getStringByColumn(CategoryTable.COLUMN_NAME)
                ?: "", it.getIntByColumn(CategoryTable.COLUMN_P_ID),
            it.getStringByColumn(CategoryTable.COLUMN_TREE) ?: ""
        ).apply {
            id = it.getIntByColumn(CategoryTable.COLUMN_ID)
        }
    }

    fun queryTreeByLike(like: String): List<Category> {
        return runCatching {
            db.createQuery(
                CategoryTable.TABLE_NAME,
                "select * from ${CategoryTable.TABLE_NAME}  where ${CategoryTable.COLUMN_TREE} like ? ORDER BY ${CategoryTable.COLUMN_ORDER} DESC",
                like
            )
                .mapToList { toCategory(it) }.timeout(6, TimeUnit.SECONDS).blockingFirst()
        }.getOrDefault(emptyList())
    }

    fun update(category: Category): Boolean {
        KLog.d("update $category")
        return runCatching {
            ioBlock {
                db.update(
                    CategoryTable.TABLE_NAME,
                    SQLiteDatabase.CONFLICT_REPLACE,
                    category.cv(true),
                    CategoryTable.COLUMN_ID + " = ${category.id!!} "
                ) > 0
            }
        }.getOrDefault(false)
    }
}