package com.example.dailylife.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Versioned payload holds type-specific form fields; relations, quantities and payment facts stay in typed rows. */
@Entity(tableName="tool_entries",indices=[Index("kind"),Index("ownerId")])
data class ToolEntry(@PrimaryKey val id:String=newId(),val ownerId:String,val kind:String,val title:String,
    val note:String="",val payload:String="{\"version\":1}",val state:String="active",val pinned:Boolean=false,
    val createdAt:Long=System.currentTimeMillis(),val updatedAt:Long=System.currentTimeMillis(),val revision:Long=1,
    val archivedAt:Long?=null,val deletedAt:Long?=null)
@Entity(tableName="tool_children",indices=[Index("entryId")])
data class ToolChild(@PrimaryKey val id:String=newId(),val entryId:String,val kind:String="check",val title:String,
    val quantityMilli:Long?=null,val unit:String="",val groupName:String="",val note:String="",val date:String?=null,
    val costMinor:Long?=null,val expenseId:String?=null,val completedAt:Long?=null,val sortOrder:Int=0)
@Entity(tableName="tool_logs",indices=[Index("entryId")])
data class ToolLog(@PrimaryKey val id:String=newId(),val entryId:String,val kind:String,val date:String,
    val quantityMilli:Long=0,val note:String="",val payload:String="{}",val createdAt:Long=System.currentTimeMillis())
@Entity(tableName="tool_links",primaryKeys=["entryId","entityType","entityId"],indices=[Index("entityId")])
data class ToolLink(val entryId:String,val entityType:String,val entityId:String,val section:String="overview",val sortOrder:Int=0)
@Entity(tableName="tool_drafts")
data class ToolDraft(@PrimaryKey val id:String,val kind:String,val payload:String,val savedAt:Long=System.currentTimeMillis())
@Entity(tableName="tool_calculations")
data class ToolCalculation(@PrimaryKey val id:String=newId(),val kind:String,val input:String,val result:String,val createdAt:Long=System.currentTimeMillis())
@Entity(tableName="tool_payment_periods",indices=[Index(value=["sourceId","originalKey"],unique=true)])
data class ToolPaymentPeriod(@PrimaryKey val id:String=newId(),val sourceId:String,val originalKey:String,
    val periodStart:String,val periodEnd:String,val dueDate:String,val expectedMinor:Long?,val status:String="pending")
@Entity(tableName="tool_payment_settlements",indices=[Index(value=["expenseId"],unique=true)])
data class ToolPaymentSettlement(@PrimaryKey val periodId:String,val actualMinor:Long,val paidOn:String,val mode:String,
    val expenseId:String?=null,val createdAt:Long=System.currentTimeMillis())
@Entity(tableName="feature_preferences")
data class FeaturePreference(@PrimaryKey val featureKey:String,val favorite:Boolean=false,val hidden:Boolean=false,
    val sortOrder:Int=0,val lastOpenedAt:Long?=null)
