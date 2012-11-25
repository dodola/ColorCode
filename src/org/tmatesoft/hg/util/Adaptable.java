/*
 * Copyright (c) 2011 TMate Software Ltd
 *  
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * For information on how to redistribute this software under
 * the terms of a license other than GNU General Public License
 * contact TMate Software at support@hg4j.com
 */
package org.tmatesoft.hg.util;

/**
 * Extension mechanism.
 * 
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public interface Adaptable {

	<T> T getAdapter(Class<T> adapterClass);

	class Factory<T> {
		
		private final Class<T> adapterClass;

		public Factory(Class<T> adapterClass) {
			assert adapterClass != null;
			this.adapterClass = adapterClass;
		}

		public T get(Object target) {
			return getAdapter(target, adapterClass, null);
		}
		
		public T get(Object target, T defaultValue) {
			return getAdapter(target, adapterClass, defaultValue);
		}
		
		/**
		 * Try to adapt target to specified class, resort to defaultValue when not possible.
		 * Instance lookup order is as follows: first, target is checked for being {@link Adaptable} and, if yes, 
		 * consulted for adapter. Then, plain {@link Class#isInstance(Object) instanceof} checks if target itself is
		 * of desired type. {@link Adaptable} check comes first to allow classed that are in fact <code>instanceof</code>
		 * desired type to respond to the demand conditionally
		 * 
		 * @param target object to adapt, <code>null</code> value, although meaningless, is tolerable.
		 * @param adapterClass desired target class
		 * @param defaultValue value to use if target cannot be adapted to desired class, may be <code>null</code>
		 * @return instance of the desired class
		 */
		public static <C> C getAdapter(Object target, Class<C> adapterClass, C defaultValue) {
			if (target instanceof Adaptable) {
				return ((Adaptable) target).getAdapter(adapterClass);
			}
			if (adapterClass.isInstance(target)) {
				return adapterClass.cast(target);
			}
			return defaultValue;
		}
	}
}
