package com.example.dailylife.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog

/** Material retains focus, input, semantics and error handling; this layer owns appearance. */
@Composable internal fun GlassButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,
    contentPadding:PaddingValues=PaddingValues(horizontal=20.dp,vertical=12.dp),content:@Composable RowScope.()->Unit) {
    val p=LocalGlass.current
    val interaction=remember {MutableInteractionSource()}
    val pressed by interaction.collectIsPressedAsState()
    val reduce=LocalReduceMotion.current
    val scale=animateFloatAsState(if(pressed && !reduce) .98f else 1f,tween(if(reduce) 0 else if(pressed) GlassMotion.PressMs else GlassMotion.ReleaseMs),label="button press")
    val shape=CircleShape
    val fill=if(p.dark) listOf(Color(0xFFCAD2FF),Color(0xFFA9B8F5)) else listOf(Color(0xFF5D6CC1),Color(0xFF505FAC))
    Button(onClick,modifier.heightIn(min=48.dp).graphicsLayer {scaleX=scale.value;scaleY=scale.value}.alpha(if(enabled) 1f else .45f)
        .clip(shape).background(Brush.linearGradient(fill)).border(1.dp,Color.White.copy(alpha=.35f),shape),
        enabled=enabled,shape=shape,interactionSource=interaction,
        colors=ButtonDefaults.buttonColors(containerColor=Color.Transparent,contentColor=if(p.dark) p.background else Color.White,
            disabledContainerColor=Color.Transparent,disabledContentColor=if(p.dark) p.background else Color.White),
        elevation=null,contentPadding=contentPadding,content=content)
}

/** Compact glass action, with a dedicated transparent lane so it never covers list content. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun GlassFloatingActionRow(label:String,tag:String,enabled:Boolean=true,onClick:()->Unit) {
    val p=LocalGlass.current
    val interaction=remember {MutableInteractionSource()}
    val pressed by interaction.collectIsPressedAsState()
    val reduce=LocalReduceMotion.current
    val scale=animateFloatAsState(if(pressed && enabled && !reduce) .96f else 1f,
        tween(if(reduce) 0 else if(pressed) GlassMotion.PressMs else GlassMotion.ReleaseMs),label="floating action press")
    val shape=CircleShape
    Box(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=8.dp),contentAlignment=Alignment.CenterEnd) {
        TooltipBox(positionProvider=TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip={PlainTooltip {Text(label)}},state=rememberTooltipState()) {
            Surface(onClick=onClick,enabled=enabled,interactionSource=interaction,shape=shape,color=Color.Transparent,
                contentColor=p.accent,modifier=Modifier.size(56.dp).testTag(tag)
                    .graphicsLayer {scaleX=scale.value;scaleY=scale.value;alpha=if(enabled) 1f else .45f}
                    .glassSurface(p,shape,selected=true,elevated=true)) {
                Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {Icon(Icons.Default.Add,label,Modifier.size(24.dp))}
            }
        }
    }
}

@Composable internal fun GlassOutlinedButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,
    contentPadding:PaddingValues=PaddingValues(horizontal=16.dp,vertical=12.dp),content:@Composable RowScope.()->Unit) {
    val p=LocalGlass.current
    OutlinedButton(onClick,modifier.heightIn(min=48.dp).glassSurface(p,CircleShape),enabled=enabled,
        shape=CircleShape,border=null,contentPadding=contentPadding,content=content)
}

@Composable internal fun GlassTonalButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,
    colors:ButtonColors=ButtonDefaults.filledTonalButtonColors(),content:@Composable RowScope.()->Unit) {
    FilledTonalButton(onClick,modifier.heightIn(min=48.dp).glassSurface(LocalGlass.current,CircleShape,true),
        enabled=enabled,shape=CircleShape,colors=colors.copy(containerColor=Color.Transparent),content=content)
}

@Composable internal fun GlassIconButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,content:@Composable ()->Unit) {
    IconButton(onClick,modifier.sizeIn(minWidth=48.dp,minHeight=48.dp)
        .glassSurface(LocalGlass.current,CircleShape),enabled=enabled,content=content)
}

@Composable internal fun GlassFab(onClick:()->Unit,modifier:Modifier=Modifier,shape:Shape=CircleShape,
    containerColor:Color=LocalGlass.current.selected,contentColor:Color=LocalGlass.current.accent,content:@Composable ()->Unit) {
    // A high-contrast action on the same rimmed material as the supplied glass button.
    FloatingActionButton(onClick,modifier.background(containerColor,shape).glassSurface(LocalGlass.current,shape,true,true),shape=shape,
        containerColor=Color.Transparent,contentColor=contentColor,elevation=FloatingActionButtonDefaults.elevation(0.dp,0.dp,0.dp,0.dp),content=content)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun GlassSaveActions(status:String,enabled:Boolean,keep:()->Unit,save:()->Unit,
    saveLabel:String,saveTag:String,keepLabel:String="稍后继续") {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if(maxWidth<340.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale>1.3f) {
            Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(status,style=MaterialTheme.typography.labelSmall,color=LocalGlass.current.muted)
                FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    TextButton(enabled=enabled,onClick=keep) {Text(keepLabel)}
                    GlassButton(enabled=enabled,onClick=save,modifier=Modifier.testTag(saveTag)) {Text(saveLabel)}
                }
            }
        } else Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(status,Modifier.weight(1f),style=MaterialTheme.typography.labelSmall,color=LocalGlass.current.muted)
            TextButton(enabled=enabled,onClick=keep) {Text(keepLabel)}
            GlassButton(enabled=enabled,onClick=save,modifier=Modifier.testTag(saveTag)) {Text(saveLabel)}
        }
    }
}

@Composable internal fun GlassExtendedFab(onClick:()->Unit,modifier:Modifier=Modifier,
    containerColor:Color=LocalGlass.current.selected,contentColor:Color=LocalGlass.current.accent,content:@Composable RowScope.()->Unit) {
    GlassFab(onClick,modifier,containerColor=containerColor,contentColor=contentColor) {
        Row(Modifier.padding(horizontal=22.dp),verticalAlignment=Alignment.CenterVertically,content=content)
    }
}

@Composable private fun glassFieldColors():TextFieldColors {
    val p=LocalGlass.current
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor=if(p.solid) p.sheet else p.sheet.copy(alpha=.86f),
        unfocusedContainerColor=if(p.solid) p.sheet else p.sheet.copy(alpha=.68f),
        disabledContainerColor=p.sheet.copy(alpha=.5f),errorContainerColor=p.sheet,
        focusedBorderColor=p.accent,unfocusedBorderColor=p.accent.copy(alpha=.19f),
        focusedLabelColor=p.accent,unfocusedLabelColor=p.muted,
        focusedPlaceholderColor=p.muted,unfocusedPlaceholderColor=p.muted)
}

@Composable internal fun GlassTextField(value:String,onValueChange:(String)->Unit,modifier:Modifier=Modifier,
    enabled:Boolean=true,readOnly:Boolean=false,textStyle:TextStyle=LocalTextStyle.current,
    label:(@Composable ()->Unit)?=null,placeholder:(@Composable ()->Unit)?=null,
    leadingIcon:(@Composable ()->Unit)?=null,trailingIcon:(@Composable ()->Unit)?=null,
    supportingText:(@Composable ()->Unit)?=null,isError:Boolean=false,
    visualTransformation:VisualTransformation=VisualTransformation.None,keyboardOptions:KeyboardOptions=KeyboardOptions.Default,
    keyboardActions:KeyboardActions=KeyboardActions.Default,singleLine:Boolean=false,maxLines:Int=if(singleLine) 1 else Int.MAX_VALUE,
    minLines:Int=1,shape:Shape=RoundedCornerShape(16.dp)) {
    OutlinedTextField(value,onValueChange,modifier.heightIn(min=52.dp),enabled,readOnly,textStyle,
        label=label,placeholder=placeholder,leadingIcon=leadingIcon,trailingIcon=trailingIcon,supportingText=supportingText,
        isError=isError,visualTransformation=visualTransformation,keyboardOptions=keyboardOptions,keyboardActions=keyboardActions,
        singleLine=singleLine,maxLines=maxLines,minLines=minLines,shape=shape,colors=glassFieldColors())
}

@Composable internal fun GlassTextField(value:TextFieldValue,onValueChange:(TextFieldValue)->Unit,modifier:Modifier=Modifier,
    enabled:Boolean=true,readOnly:Boolean=false,textStyle:TextStyle=LocalTextStyle.current,
    label:(@Composable ()->Unit)?=null,placeholder:(@Composable ()->Unit)?=null,
    leadingIcon:(@Composable ()->Unit)?=null,trailingIcon:(@Composable ()->Unit)?=null,
    supportingText:(@Composable ()->Unit)?=null,isError:Boolean=false,
    visualTransformation:VisualTransformation=VisualTransformation.None,keyboardOptions:KeyboardOptions=KeyboardOptions.Default,
    keyboardActions:KeyboardActions=KeyboardActions.Default,singleLine:Boolean=false,maxLines:Int=if(singleLine) 1 else Int.MAX_VALUE,
    minLines:Int=1,shape:Shape=RoundedCornerShape(16.dp)) {
    OutlinedTextField(value,onValueChange,modifier.heightIn(min=52.dp),enabled,readOnly,textStyle,
        label=label,placeholder=placeholder,leadingIcon=leadingIcon,trailingIcon=trailingIcon,supportingText=supportingText,
        isError=isError,visualTransformation=visualTransformation,keyboardOptions=keyboardOptions,keyboardActions=keyboardActions,
        singleLine=singleLine,maxLines=maxLines,minLines=minLines,shape=shape,colors=glassFieldColors())
}

@Composable internal fun GlassSwitch(checked:Boolean,onCheckedChange:((Boolean)->Unit)?,modifier:Modifier=Modifier,enabled:Boolean=true) {
    val p=LocalGlass.current
    Switch(checked,onCheckedChange,modifier.heightIn(min=48.dp),enabled=enabled,
        colors=SwitchDefaults.colors(checkedThumbColor=Color.White,checkedTrackColor=p.accent,checkedBorderColor=p.accent,
            uncheckedThumbColor=p.muted,uncheckedTrackColor=p.selected.copy(alpha=.55f),uncheckedBorderColor=p.accent.copy(alpha=.28f)))
}

@Composable internal fun GlassCheckbox(checked:Boolean,onCheckedChange:((Boolean)->Unit)?,modifier:Modifier=Modifier,enabled:Boolean=true) {
    Checkbox(checked,onCheckedChange,modifier,enabled=enabled,
        colors=CheckboxDefaults.colors(checkedColor=LocalGlass.current.accent,uncheckedColor=LocalGlass.current.muted,
            checkmarkColor=if(LocalGlass.current.dark) LocalGlass.current.background else Color.White))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun GlassAlertDialog(onDismissRequest:()->Unit,confirmButton:@Composable ()->Unit,
    modifier:Modifier=Modifier,dismissButton:(@Composable ()->Unit)?=null,title:(@Composable ()->Unit)?=null,
    text:(@Composable ()->Unit)?=null,containerColor:Color=LocalGlass.current.sheet) {
    Dialog(onDismissRequest) {
        GlassPanel(modifier.fillMaxWidth(),radius=28) {
            Column(Modifier.heightIn(max=560.dp).verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                title?.let {ProvideTextStyle(MaterialTheme.typography.titleLarge) {it()}}
                text?.let {ProvideTextStyle(MaterialTheme.typography.bodyLarge) {it()}}
                FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    dismissButton?.invoke();confirmButton()
                }
            }
        }
    }
}

@Composable internal fun GlassPageHeader(title:String,back:(()->Unit)?=null,enabled:Boolean=true,backTag:String="back",actions:@Composable RowScope.()->Unit={}) {
    Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp).heightIn(min=52.dp),verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        if(back!=null) GlassIconButton(back,modifier=Modifier.testTag(backTag),enabled=enabled) {
            Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,"返回")
        }
        Text(title,Modifier.weight(1f),style=if(back==null) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium)
        actions()
    }
}
