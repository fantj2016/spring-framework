/*
 * Copyright 2002-2008 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.web.servlet.view;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import junit.framework.AssertionFailedError;
import junit.framework.TestCase;
import org.easymock.MockControl;
import static org.easymock.EasyMock.*;
import org.junit.Test;
import static org.junit.Assert.*;

import org.springframework.beans.TestBean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.View;

/**
 * Tests for redirect view, and query string construction.
 * Doesn't test URL encoding, although it does check that it's called.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @author Arjen Poutsma
 * @since 27.05.2003
 */
public class RedirectViewTests {

	@Test(expected = IllegalArgumentException.class)
	public void noUrlSet() throws Exception {
		RedirectView rv = new RedirectView();
		rv.afterPropertiesSet();
	}

	@Test
	public void http11() throws Exception {
		RedirectView rv = new RedirectView();
		rv.setUrl("http://url.somewhere.com");
		rv.setHttp10Compatible(false);
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		rv.render(new HashMap(), request, response);
		assertEquals(303, response.getStatus());
		assertEquals("http://url.somewhere.com", response.getHeader("Location"));
	}

	@Test
	public void explicitStatusCode() throws Exception {
		RedirectView rv = new RedirectView();
		rv.setUrl("http://url.somewhere.com");
		rv.setHttp10Compatible(false);
		rv.setStatusCode(HttpStatus.CREATED);
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		rv.render(new HashMap(), request, response);
		assertEquals(201, response.getStatus());
		assertEquals("http://url.somewhere.com", response.getHeader("Location"));
	}

	@Test
	public void attributeStatusCode() throws Exception {
		RedirectView rv = new RedirectView();
		rv.setUrl("http://url.somewhere.com");
		rv.setHttp10Compatible(false);
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setAttribute(View.RESPONSE_STATUS_ATTRIBUTE, HttpStatus.CREATED);
		MockHttpServletResponse response = new MockHttpServletResponse();
		rv.render(new HashMap(), request, response);
		assertEquals(201, response.getStatus());
		assertEquals("http://url.somewhere.com", response.getHeader("Location"));
	}

	@Test
	public void emptyMap() throws Exception {
		String url = "/myUrl";
		doTest(new HashMap(), url, false, url);
	}

	@Test
	public void emptyMapWithContextRelative() throws Exception {
		String url = "/myUrl";
		doTest(new HashMap(), url, true, url);
	}

	@Test
	public void singleParam() throws Exception {
		String url = "http://url.somewhere.com";
		String key = "foo";
		String val = "bar";
		Map model = new HashMap();
		model.put(key, val);
		String expectedUrlForEncoding = url + "?" + key + "=" + val;
		doTest(model, url, false, expectedUrlForEncoding);
	}

	@Test
	public void singleParamWithoutExposingModelAttributes() throws Exception {
		String url = "http://url.somewhere.com";
		String key = "foo";
		String val = "bar";
		Map model = new HashMap();
		model.put(key, val);
		String expectedUrlForEncoding = url; // + "?" + key + "=" + val;
		doTest(model, url, false, false, expectedUrlForEncoding);
	}

	@Test
	public void paramWithAnchor() throws Exception {
		String url = "http://url.somewhere.com/test.htm#myAnchor";
		String key = "foo";
		String val = "bar";
		Map model = new HashMap();
		model.put(key, val);
		String expectedUrlForEncoding = "http://url.somewhere.com/test.htm" + "?" + key + "=" + val + "#myAnchor";
		doTest(model, url, false, expectedUrlForEncoding);
	}

	@Test
	public void twoParams() throws Exception {
		String url = "http://url.somewhere.com";
		String key = "foo";
		String val = "bar";
		String key2 = "thisIsKey2";
		String val2 = "andThisIsVal2";
		Map model = new HashMap();
		model.put(key, val);
		model.put(key2, val2);
		try {
			String expectedUrlForEncoding = "http://url.somewhere.com?" + key + "=" + val + "&" + key2 + "=" + val2;
			doTest(model, url, false, expectedUrlForEncoding);
		}
		catch (AssertionFailedError err) {
			// OK, so it's the other order... probably on Sun JDK 1.6 or IBM JDK 1.5
			String expectedUrlForEncoding = "http://url.somewhere.com?" + key2 + "=" + val2 + "&" + key + "=" + val;
			doTest(model, url, false, expectedUrlForEncoding);
		}
	}

	@Test
	public void arrayParam() throws Exception {
		String url = "http://url.somewhere.com";
		String key = "foo";
		String[] val = new String[] {"bar", "baz"};
		Map model = new HashMap();
		model.put(key, val);
		try {
			String expectedUrlForEncoding = "http://url.somewhere.com?" + key + "=" + val[0] + "&" + key + "=" + val[1];
			doTest(model, url, false, expectedUrlForEncoding);
		}
		catch (AssertionFailedError err) {
			// OK, so it's the other order... probably on Sun JDK 1.6 or IBM JDK 1.5
			String expectedUrlForEncoding = "http://url.somewhere.com?" + key + "=" + val[1] + "&" + key + "=" + val[0];
			doTest(model, url, false, expectedUrlForEncoding);
		}
	}

	@Test
	public void collectionParam() throws Exception {
		String url = "http://url.somewhere.com";
		String key = "foo";
		List val = new ArrayList();
		val.add("bar");
		val.add("baz");
		Map model = new HashMap();
		model.put(key, val);
		try {
			String expectedUrlForEncoding = "http://url.somewhere.com?" + key + "=" + val.get(0) + "&" + key + "=" + val.get(1);
			doTest(model, url, false, expectedUrlForEncoding);
		}
		catch (AssertionFailedError err) {
			// OK, so it's the other order... probably on Sun JDK 1.6 or IBM JDK 1.5
			String expectedUrlForEncoding = "http://url.somewhere.com?" + key + "=" + val.get(1) + "&" + key + "=" + val.get(0);
			doTest(model, url, false, expectedUrlForEncoding);
		}
	}

	@Test
	public void objectConversion() throws Exception {
		String url = "http://url.somewhere.com";
		String key = "foo";
		String val = "bar";
		String key2 = "int2";
		Object val2 = new Long(611);
		Object key3 = "tb";
		Object val3 = new TestBean();
		Map model = new LinkedHashMap();
		model.put(key, val);
		model.put(key2, val2);
		model.put(key3, val3);
		String expectedUrlForEncoding = "http://url.somewhere.com?" + key + "=" + val + "&" + key2 + "=" + val2;
		doTest(model, url, false, expectedUrlForEncoding);
	}

	private void doTest(Map map, String url, boolean contextRelative, String expectedUrlForEncoding)
			throws Exception {
		doTest(map, url, contextRelative, true, expectedUrlForEncoding);
	}

	private void doTest(final Map<String, ?> map, final String url, final boolean contextRelative,
			final boolean exposeModelAttributes, String expectedUrlForEncoding) throws Exception {

		class TestRedirectView extends RedirectView {

			public boolean queryPropertiesCalled = false;

			/**
			 * Test whether this callback method is called with correct args
			 */
			protected Map queryProperties(Map model) {
				// They may not be the same model instance, but they're still equal
				assertTrue("Map and model must be equal.", map.equals(model));
				this.queryPropertiesCalled = true;
				return super.queryProperties(model);
			}
		}

		TestRedirectView rv = new TestRedirectView();
		rv.setUrl(url);
		rv.setContextRelative(contextRelative);
		rv.setExposeModelAttributes(exposeModelAttributes);

		MockControl requestControl = MockControl.createControl(HttpServletRequest.class);
		HttpServletRequest request = (HttpServletRequest) requestControl.getMock();
		request.getCharacterEncoding();
		requestControl.setReturnValue(null, 1);
		if (contextRelative) {
			expectedUrlForEncoding = "/context" + expectedUrlForEncoding;
			request.getContextPath();
			requestControl.setReturnValue("/context");
		}
		requestControl.replay();

		MockControl responseControl = MockControl.createControl(HttpServletResponse.class);
		HttpServletResponse resp = (HttpServletResponse) responseControl.getMock();
		resp.encodeRedirectURL(expectedUrlForEncoding);
		responseControl.setReturnValue(expectedUrlForEncoding);
		resp.sendRedirect(expectedUrlForEncoding);
		responseControl.setVoidCallable(1);
		responseControl.replay();

		rv.render(map, request, resp);
		if (exposeModelAttributes) {
			assertTrue("queryProperties() should have been called.", rv.queryPropertiesCalled);
		}
		responseControl.verify();
	}

}
