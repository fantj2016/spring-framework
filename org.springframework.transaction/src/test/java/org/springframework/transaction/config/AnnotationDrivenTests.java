/*
 * Copyright 2002-2007 the original author or authors.
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

package org.springframework.transaction.config;

import junit.framework.TestCase;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.transaction.CallCountingTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * @author Rob Harrop
 * @author Juergen Hoeller
 */
public class AnnotationDrivenTests extends TestCase {

	public void testWithProxyTargetClass() throws Exception {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("annotationDrivenProxyTargetClassTests.xml", getClass());
		CallCountingTransactionManager tm1 = context.getBean("transactionManager1", CallCountingTransactionManager.class);
		CallCountingTransactionManager tm2 = context.getBean("transactionManager2", CallCountingTransactionManager.class);
		TransactionalService service = context.getBean("service", TransactionalService.class);
		assertTrue(AopUtils.isCglibProxy(service));
		service.setSomething("someName");
		assertEquals(1, tm1.commits);
		assertEquals(0, tm2.commits);
		service.doSomething();
		assertEquals(1, tm1.commits);
		assertEquals(1, tm2.commits);
		service.setSomething("someName");
		assertEquals(2, tm1.commits);
		assertEquals(1, tm2.commits);
		service.doSomething();
		assertEquals(2, tm1.commits);
		assertEquals(2, tm2.commits);
	}


	public static class TransactionCheckingInterceptor implements MethodInterceptor {

		public Object invoke(MethodInvocation methodInvocation) throws Throwable {
			if (methodInvocation.getMethod().getName().equals("setSomething")) {
				assertTrue(TransactionSynchronizationManager.isSynchronizationActive());
			}
			else {
				assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
			}
			return methodInvocation.proceed();
		}
	}

}