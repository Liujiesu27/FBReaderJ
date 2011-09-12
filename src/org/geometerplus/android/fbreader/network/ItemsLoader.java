/*
 * Copyright (C) 2010-2011 Geometer Plus <contact@geometerplus.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301, USA.
 */

package org.geometerplus.android.fbreader.network;

import java.util.Set;

import android.app.Activity;

import org.geometerplus.zlibrary.core.network.ZLNetworkException;

import org.geometerplus.fbreader.network.*;

public abstract class ItemsLoader<T extends NetworkTree> extends NetworkItemsLoader<T> {
	protected final Activity myActivity;

	private volatile boolean myFinishProcessed;
	private final Object myFinishMonitor = new Object();

	private volatile boolean myFinished;
	private volatile Runnable myPostRunnable;
	private final Object myFinishedLock = new Object();

	public ItemsLoader(Activity activity, T tree) {
		super(tree);
		myActivity = activity;
	}

	public final void run() {
		final NetworkLibrary library = NetworkLibrary.Instance();

		try {
			library.storeLoader(getTree(), this);
			library.fireModelChangedEvent(NetworkLibrary.ChangeListener.Code.SomeCode);

			try {
				doBefore();
			} catch (ZLNetworkException e) {
				finishOnUiThread(e.getMessage(), false);
				return;
			}
			String error = null;
			try {
				doLoading();
			} catch (ZLNetworkException e) {
				error = e.getMessage();
			}

			finishOnUiThread(error, isLoadingInterrupted());
			ensureFinishProcessed();
		} finally {
			library.removeStoredLoader(getTree());
			synchronized (myFinishedLock) {
				if (myPostRunnable != null) {
					myPostRunnable.run();
				}
				myFinished = true;
			}
			library.fireModelChangedEvent(NetworkLibrary.ChangeListener.Code.SomeCode);
		}
	}

	public void setPostRunnable(Runnable runnable) {
		if (myPostRunnable != null) {
			return;
		}
		synchronized (myFinishedLock) {
			if (myFinished) {
				runnable.run();
			} else {
				myPostRunnable = runnable;
			}
		}
	}

	private final void ensureFinishProcessed() {
		synchronized (myFinishMonitor) {
			while (!myFinishProcessed) {
				try {
					myFinishMonitor.wait();
				} catch (InterruptedException e) {
				}
			}
		}
	}

	private final void finishOnUiThread(final String errorMessage, final boolean interrupted) {
		myActivity.runOnUiThread(new Runnable() {
			public void run() {
				synchronized (myFinishMonitor) {
					onFinish(errorMessage, interrupted, uncommitedItems());
					myFinishProcessed = true;
					// wake up process, that waits for finish condition (see ensureFinish() method)
					myFinishMonitor.notifyAll();
				}
			}
		});
	}

	protected abstract void onFinish(String errorMessage, boolean interrupted, Set<NetworkItem> uncommitedItems);

	protected abstract void doBefore() throws ZLNetworkException;
	protected abstract void doLoading() throws ZLNetworkException;
}
