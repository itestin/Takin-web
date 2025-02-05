/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.shulie.takin.jmeter.functions;

import org.apache.commons.jexl3.*;
import org.apache.jmeter.engine.util.CompoundVariable;
import org.apache.jmeter.functions.AbstractFunction;
import org.apache.jmeter.functions.InvalidVariableException;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.samplers.Sampler;
import org.apache.jmeter.testelement.ThreadListener;
import org.apache.jmeter.threads.JMeterContext;
import org.apache.jmeter.threads.JMeterContextService;
import org.apache.jmeter.threads.JMeterVariables;
import org.apache.jmeter.util.JMeterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * A function which understands Commons JEXL3
 * @since 3.0
 */
// For unit tests, see TestJexlFunction
public class Jexl3Function extends AbstractFunction implements ThreadListener {

    private static final Logger log = LoggerFactory.getLogger(Jexl3Function.class);

    private static final String KEY = "__jexl3"; //$NON-NLS-1$

    private static final List<String> desc = new LinkedList<>();

    private static final ThreadLocal<JexlEngine> threadLocalJexl = ThreadLocal
            .withInitial(Jexl3Function::createJexlEngine);

    static
    {
        desc.add(JMeterUtils.getResString("jexl_expression")); //$NON-NLS-1$
        desc.add(JMeterUtils.getResString("function_name_paropt"));// $NON-NLS1$
    }

    private Object[] values;

    /** {@inheritDoc} */
    @Override
    public String execute(SampleResult previousResult, Sampler currentSampler)
            throws InvalidVariableException
    {
        String str = ""; //$NON-NLS-1$

        CompoundVariable var = (CompoundVariable) values[0];
        String exp = var.execute();

        String varName = ""; //$NON-NLS-1$
        if (values.length > 1) {
            varName = ((CompoundVariable) values[1]).execute().trim();
        }

        JMeterContext jmctx = JMeterContextService.getContext();
        JMeterVariables vars = jmctx.getVariables();

        try
        {
            JexlContext jc = new MapContext();
            jc.set("log", log); //$NON-NLS-1$
            jc.set("ctx", jmctx); //$NON-NLS-1$
            jc.set("vars", vars); //$NON-NLS-1$
            jc.set("props", JMeterUtils.getJMeterProperties()); //$NON-NLS-1$
            // Previously mis-spelt as theadName
            jc.set("threadName", Thread.currentThread().getName()); //$NON-NLS-1$
            jc.set("sampler", currentSampler); //$NON-NLS-1$ (may be null)
            jc.set("sampleResult", previousResult); //$NON-NLS-1$ (may be null)
            jc.set("OUT", System.out);//$NON-NLS-1$

            // Now evaluate the script, getting the result
            JexlScript e = threadLocalJexl.get().createScript(exp);
            Object o = e.execute(jc);
            if (o != null)
            {
                str = o.toString();
            }
            if (vars != null && varName.length() > 0) {// vars will be null on TestPlan
                vars.put(varName, str);
            }
        } catch (Exception e) {
            log.error("An error occurred while evaluating the expression \"{}\"\n", exp, e);
        }
        return str;
    }

    private static JexlEngine createJexlEngine() {
        return new JexlBuilder()
                .cache(512)
                .silent(true)
                .strict(true)
                // debug is true by default an impact negatively performances
                // by a factory of 10
                // Use JexlInfo if necessary
                .debug(false)
                .create();
    }

    /** {@inheritDoc} */
    @Override
    public List<String> getArgumentDesc()
    {
        return desc;
    }

    /** {@inheritDoc} */
    @Override
    public String getReferenceKey()
    {
        return KEY;
    }

    /** {@inheritDoc} */
    @Override
    public void setParameters(Collection<CompoundVariable> parameters)
            throws InvalidVariableException
    {
        checkParameterCount(parameters, 1, 2);
        values = parameters.toArray();
    }

    @Override
    public void threadStarted() {
        // nothing to do on thread startup
    }

    @Override
    public void threadFinished() {
        JexlEngine engine = threadLocalJexl.get();
        if(engine != null) {
            engine.clearCache();
            threadLocalJexl.remove();
        }
    }

}
