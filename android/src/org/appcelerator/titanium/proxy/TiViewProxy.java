/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.titanium.proxy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;

import org.appcelerator.titanium.TiContext;
import org.appcelerator.titanium.TiDict;
import org.appcelerator.titanium.TiProxy;
import org.appcelerator.titanium.kroll.KrollCallback;
import org.appcelerator.titanium.util.AsyncResult;
import org.appcelerator.titanium.util.Log;
import org.appcelerator.titanium.util.TiConfig;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.view.TiUIView;

import ti.modules.titanium.ui._2DMatrixProxy;
import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.RotateAnimation;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;

public abstract class TiViewProxy extends TiProxy implements Handler.Callback
{
	private static final String LCAT = "TiViewProxy";
	private static final boolean DBG = TiConfig.LOGD;

	private static final int MSG_FIRST_ID = TiProxy.MSG_LAST_ID + 1;

	private static final int MSG_GETVIEW = MSG_FIRST_ID + 100;
	private static final int MSG_FIRE_PROPERTY_CHANGES = MSG_FIRST_ID + 101;
	private static final int MSG_ADD_CHILD = MSG_FIRST_ID + 102;
	private static final int MSG_REMOVE_CHILD = MSG_FIRST_ID + 103;
	private static final int MSG_INVOKE_METHOD = MSG_FIRST_ID + 104;
	private static final int MSG_BLUR = MSG_FIRST_ID + 105;
	private static final int MSG_FOCUS = MSG_FIRST_ID + 106;
	private static final int MSG_SHOW = MSG_FIRST_ID + 107;
	private static final int MSG_HIDE = MSG_FIRST_ID + 108;

	protected static final int MSG_LAST_ID = MSG_FIRST_ID + 999;

	protected ArrayList<TiViewProxy> children;

	private static class InvocationWrapper {
		public String name;
		public Method m;
		public Object target;
		public Object[] args;
	}

	// Ti Properties force using accessors.
	private Double zIndex;
	private int opaque;
	private int opacity;
	private String bgColor; // We've spelled out background in other places.

	protected TiUIView view;

	public TiViewProxy(TiContext tiContext, Object[] args)
	{
		super(tiContext);
		if (args.length > 0) {
			setProperties((TiDict) args[0]);
		}
	}

	//This handler callback is tied to the UI thread.
	public boolean handleMessage(Message msg)
	{
		switch(msg.what) {
			case MSG_GETVIEW : {
				AsyncResult result = (AsyncResult) msg.obj;
				result.setResult(handleGetView((Activity) result.getArg()));
				return true;
			}
			case MSG_FIRE_PROPERTY_CHANGES : {
				handleFirePropertyChanges();
				return true;
			}
			case MSG_ADD_CHILD : {
				AsyncResult result = (AsyncResult) msg.obj;
				handleAdd((TiViewProxy) result.getArg());
				result.setResult(null); //Signal added.
				return true;
			}
			case MSG_REMOVE_CHILD : {
				AsyncResult result = (AsyncResult) msg.obj;
				handleRemove((TiViewProxy) result.getArg());
				result.setResult(null); //Signal removed.
				return true;
			}
			case MSG_INVOKE_METHOD : {
				AsyncResult result = (AsyncResult) msg.obj;
				result.setResult(handleInvokeMethod((InvocationWrapper) result.getArg()));
				return true;
			}
			case MSG_BLUR : {
				handleBlur();
				return true;
			}
			case MSG_FOCUS : {
				handleFocus();
				return true;
			}
			case MSG_SHOW : {
				handleShow((TiDict) msg.obj);
				return true;
			}
			case MSG_HIDE : {
				handleHide((TiDict) msg.obj);
				return true;
			}
		}
		return super.handleMessage(msg);
	}

	public Context getContext()
	{
		return getTiContext().getActivity();
	}

	public String getZIndex() {
		return zIndex == null ? (String) null : String.valueOf(zIndex);
	}

	public void setZIndex(String value) {
		if (value != null && value.trim().length() > 0) {
			zIndex = new Double(value);
		}
	}

	public void clearView() {
		if (view != null) {
			view.release();
		}
		view = null;
	}

	public TiUIView peekView()
	{
		return view;
	}

	public TiUIView getView(Activity activity)
	{
		if (activity == null) {
			activity = getTiContext().getActivity();
		}
		if(getTiContext().isUIThread()) {
			return handleGetView(activity);
		}

		AsyncResult result = new AsyncResult(activity);
		Message msg = getUIHandler().obtainMessage(MSG_GETVIEW, result);
		msg.sendToTarget();
		return (TiUIView) result.getResult();
	}

	protected TiUIView handleGetView(Activity activity)
	{
		if (view == null) {
			if (DBG) {
				Log.i(LCAT, "getView: " + getClass().getSimpleName());
			}

			view = createView(activity);
			realizeViews(activity, view);
		}
		return view;
	}

	public void realizeViews(Activity activity, TiUIView view)
	{

		modelListener = view;
		modelListener.processProperties(dynprops != null ? new TiDict(dynprops) : new TiDict());

		// Use a copy so bundle can be modified as it passes up the inheritance
		// tree. Allows defaults to be added and keys removed.


		if (children != null) {
			for (TiViewProxy p : children) {
				TiUIView cv = p.getView(activity);
				view.add(cv);
			}
		}

//		View nativeView = view.getNativeView();
//		if (nativeView != null) {
//			Log.e(LCAT, "native view type: " + nativeView.getClass().getSimpleName());
//		}
//		if (nativeView instanceof ViewGroup) {
//			ViewGroup vg = (ViewGroup) nativeView;
//			if (children != null) {
//				int i = 0;
//				for(TiViewProxy p : children) {
//					TiUIView v = p.getView(activity);
//					Log.e(LCAT, "attaching: " + v.getClass().getSimpleName());
//
//					v.setParent(this);
//					TitaniumCompositeLayout.TitaniumCompositeLayoutParams params = v.getLayoutParams();
//					// the index needs to be set. It's consulted as a last resort when considering
//					// zIndex
//					params.index = i++;
//					Log.w(LCAT, "native view for: " + v.getNativeView().getId());
//					vg.addView(v.getNativeView(), params);
//				}
//			}
//		} else {
//			if (children != null && children.size() > 0) {
//				Log.w(LCAT, "Children added to non ViewGroup parent ignored.");
//			}
//		}
	}

	public void releaseViews() {
		if (view != null) {
			if  (children != null) {
				for(TiViewProxy p : children) {
					p.releaseViews();
				}
			}
			view.release();
		}
	}

	public abstract TiUIView createView(Activity activity);

	public void add(TiViewProxy child) {
		if (children == null) {
			children = new ArrayList<TiViewProxy>();
		}
		if (peekView() != null) {
			if(getTiContext().isUIThread()) {
				handleAdd(child);
				return;
			}

			AsyncResult result = new AsyncResult(child);
			Message msg = getUIHandler().obtainMessage(MSG_ADD_CHILD, result);
			msg.sendToTarget();
			result.getResult(); // We don't care about the result, just synchronizing.
		} else {
			children.add(child);
		}
		//TODO zOrder
	}

	public void handleAdd(TiViewProxy child)
	{
		children.add(child);
		if (view != null) {
			TiUIView cv = child.getView(getTiContext().getActivity());
			view.add(cv);
		}
	}

	public void remove(TiViewProxy child)
	{
		if (peekView() != null) {
			if (getTiContext().isUIThread()) {
				handleRemove(child);
				return;
			}

			AsyncResult result = new AsyncResult(child);
			Message msg = getUIHandler().obtainMessage(MSG_REMOVE_CHILD, result);
			msg.sendToTarget();
			result.getResult(); // We don't care about the result, just synchronizing.
		} else {
			if (children != null) {
				children.remove(child);
			}
		}
	}

	public void handleRemove(TiViewProxy child)
	{
		if (children != null) {
			children.remove(child);
			if (view != null) {
				view.remove(child.peekView());
			}
		}
	}

	public void show(TiDict options)
	{
		if (getTiContext().isUIThread()) {
			handleShow(options);
		} else {
			getUIHandler().obtainMessage(MSG_SHOW, options).sendToTarget();
		}
	}
	
	protected void handleShow(TiDict options) {
		view.show();
	}

	public void hide(TiDict options) {
		if (getTiContext().isUIThread()) {
			handleHide(options);
		} else {
			getUIHandler().obtainMessage(MSG_HIDE, options).sendToTarget();
		}

	}
	
	protected void handleHide(TiDict options) {
		view.hide();
	}

	public void animate(TiDict options, KrollCallback callback)
	{
		_2DMatrixProxy tdm = null;
		Double delay = null;
		Double duration = null;
		Double opacity = null;

		if (options.containsKey("transform")) {
			tdm = (_2DMatrixProxy) options.get("transform");
		}
		if (options.containsKey("delay")) {
			delay = TiConvert.toDouble(options, "delay");
		}
		if (options.containsKey("duration")) {
			duration = TiConvert.toDouble(options, "duration");
		}
		if (options.containsKey("opacity")) {
			opacity = TiConvert.toDouble(options, "opacity");
		}

		if (tdm != null) {
			AnimationSet as = new AnimationSet(false);
			as.setFillAfter(true);
			if (tdm.hasTranslation()) {
				Animation a = new TranslateAnimation(0.0f, tdm.getXTranslation(),0.0f, tdm.getYTranslation());
				as.addAnimation(a);
			}
			if (tdm.hasScaleFactor()) {
				Animation a = new ScaleAnimation(1,tdm.getScaleFactor(), 1, tdm.getScaleFactor());
				as.addAnimation(a);
			}
			if (tdm.hasRotation()) {
				Animation a = new RotateAnimation(0,tdm.getRotation());
				as.addAnimation(a);
			}
			// Set duration after adding children.
			if (duration != null) {
				as.setDuration(duration.longValue());
			}
			if (delay != null) {
				as.setStartTime(delay.longValue());
			}

			if (callback != null) {
				final KrollCallback kb = callback;
				as.setAnimationListener(new Animation.AnimationListener(){

					@Override
					public void onAnimationEnd(Animation a) {
						if (kb != null) {
							kb.call();
						}
					}

					@Override
					public void onAnimationRepeat(Animation a) {
					}

					@Override
					public void onAnimationStart(Animation a) {
					}

				});
			}
			TiUIView tiv = peekView();
			if (tiv != null) {
				tiv.animate(as);
			}
		} else {
			if (callback != null) {
				callback.call();
			}
		}
	}

	public void blur()
	{
		if (getTiContext().isUIThread()) {
			handleBlur();
		} else {
			getUIHandler().sendEmptyMessage(MSG_BLUR);
		}
	}
	protected void handleBlur() {
		if (view != null) {
			view.blur();
		}
	}
	public void focus()
	{
		if (getTiContext().isUIThread()) {
			handleFocus();
		} else {
			getUIHandler().sendEmptyMessage(MSG_FOCUS);
		}
	}
	protected void handleFocus() {
		if (view != null) {
			view.focus();
		}
	}


	// Helper methods

	private void firePropertyChanges() {
		if (getTiContext().isUIThread()) {
			handleFirePropertyChanges();
		} else {
			getUIHandler().sendEmptyMessage(MSG_FIRE_PROPERTY_CHANGES);
		}
	}

	private void handleFirePropertyChanges() {
		if (modelListener != null && dynprops != null) {
			for (String key : dynprops.keySet()) {
				modelListener.propertyChanged(key, null, dynprops.get(key), this);
			}
		}
	}

	@Override
	public Object resultForUndefinedMethod(String name, Object[] args)
	{
		if (view != null) {
			Method m = getTiContext().getTiApp().methodFor(view.getClass(), name);
			if (m != null) {
				InvocationWrapper w = new InvocationWrapper();
				w.name = name;
				w.m = m;
				w.target = view;
				w.args = args;

				if (getTiContext().isUIThread()) {
					handleInvokeMethod(w);
				} else {
					AsyncResult result = new AsyncResult(w);
					Message msg = getUIHandler().obtainMessage(MSG_INVOKE_METHOD, result);
					msg.sendToTarget();
					return result.getResult();
				}
			}
		}

		return super.resultForUndefinedMethod(name, args);
	}

	private Object handleInvokeMethod(InvocationWrapper w)
	{
		try {
			return w.m.invoke(w.target, w.args);
		} catch (InvocationTargetException e) {
			Log.e(LCAT, "Error while invoking " + w.name + " on " + view.getClass().getSimpleName(), e);
			// TODO - wrap in a better exception.
			return e;
		} catch (IllegalAccessException e) {
			Log.e(LCAT, "Error while invoking " + w.name + " on " + view.getClass().getSimpleName(), e);
			return e;
		}
	}
}
