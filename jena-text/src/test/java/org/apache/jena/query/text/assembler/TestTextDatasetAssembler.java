/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.jena.query.text.assembler;

import org.apache.jena.query.text.DatasetGraphText ;
import org.apache.jena.query.text.TextDocProducer ;
import org.apache.jena.query.text.TextIndex ;
import org.apache.jena.query.text.TextIndexLucene ;
import org.apache.jena.query.text.TextQuery ;
import org.junit.Ignore;
import org.junit.Test ;

import com.hp.hpl.jena.assembler.Assembler ;
import com.hp.hpl.jena.assembler.exceptions.AssemblerException ;
import com.hp.hpl.jena.graph.Node ;
import com.hp.hpl.jena.query.Dataset ;
import com.hp.hpl.jena.rdf.model.Resource ;
import com.hp.hpl.jena.sparql.core.DatasetGraph;
import com.hp.hpl.jena.sparql.core.QuadAction ;
import com.hp.hpl.jena.tdb.assembler.AssemblerTDB ;
import com.hp.hpl.jena.vocabulary.RDF ;

import static org.junit.Assert.* ;

/**
 * Test the text dataset assembler.
 */
public class TestTextDatasetAssembler extends AbstractTestTextAssembler {
	
	private static final String TESTBASE = "http://example.org/testDatasetAssembler/";
	
	private static final Resource spec1;
	private static final Resource noDatasetPropertySpec;
	private static final Resource noIndexPropertySpec;
	private static final Resource customTextDocProducerSpec;
	private static final Resource customDyadicTextDocProducerSpec;
	
	@Test
	public void testSimpleDatasetAssembler() {
		Dataset dataset = (Dataset) Assembler.general.open(spec1);
		assertTrue(dataset.getContext().get(TextQuery.textIndex) instanceof TextIndexLucene);
	}
	
	@Test(expected = AssemblerException.class)
	public void testErrorOnNoDataset() {
	    Assembler.general.open(noDatasetPropertySpec);
	}
	
	@Test(expected = AssemblerException.class)
	public void testErrorOnNoIndex() {
	    Assembler.general.open(noIndexPropertySpec);
	}
	
	@Test
	public void testCustomTextDocProducer() {
	    Dataset dataset = (Dataset)Assembler.general.open(customTextDocProducerSpec) ;
	    DatasetGraphText dsgText = (DatasetGraphText)dataset.asDatasetGraph() ;
        assertTrue(dsgText.getMonitor() instanceof CustomTextDocProducer) ;
        dataset.close();
	}
	
	@Test
	public void testCustomTextDocProducerDyadicConstructor() {
	    Dataset dataset = (Dataset)Assembler.general.open(customDyadicTextDocProducerSpec) ;
	    DatasetGraphText dsgText = (DatasetGraphText)dataset.asDatasetGraph() ;
        assertTrue(dsgText.getMonitor() instanceof CustomDyadicTextDocProducer) ;
        dataset.close();
	}
	
	static {
		TextAssembler.init();
		AssemblerTDB.init();
		spec1 =
				model.createResource(TESTBASE + "spec1")
				     .addProperty(RDF.type, TextVocab.textDataset)
				     .addProperty(TextVocab.pDataset, SIMPLE_DATASET_SPEC)
				     .addProperty(TextVocab.pIndex, SIMPLE_INDEX_SPEC3);
		noDatasetPropertySpec =
				model.createResource(TESTBASE + "noDatasetPropertySpec")
				     .addProperty(RDF.type, TextVocab.textDataset)
				     .addProperty(TextVocab.pIndex, SIMPLE_INDEX_SPEC4);
		noIndexPropertySpec =
				model.createResource(TESTBASE + "noIndexPropertySpec")
				     .addProperty(RDF.type, TextVocab.textDataset)
				     .addProperty(TextVocab.pDataset, SIMPLE_DATASET_SPEC);
		customTextDocProducerSpec =
                model.createResource(TESTBASE + "customTextDocProducerSpec")
                     .addProperty(RDF.type, TextVocab.textDataset)
                     .addProperty(TextVocab.pDataset, SIMPLE_DATASET_SPEC)
                     .addProperty(TextVocab.pIndex, SIMPLE_INDEX_SPEC5)
                     .addProperty(TextVocab.pTextDocProducer, model.createResource("java:org.apache.jena.query.text.assembler.TestTextDatasetAssembler$CustomTextDocProducer"));
		
		customDyadicTextDocProducerSpec =
                model.createResource(TESTBASE + "customDyadicTextDocProducerSpec")
                     .addProperty(RDF.type, TextVocab.textDataset)
                     .addProperty(TextVocab.pDataset, SIMPLE_DATASET_SPEC)
                     .addProperty(TextVocab.pIndex, SIMPLE_INDEX_SPEC5)
                     .addProperty(TextVocab.pTextDocProducer, model.createResource("java:org.apache.jena.query.text.assembler.TestTextDatasetAssembler$CustomDyadicTextDocProducer"));
		
	}
	
	private static class CustomTextDocProducer implements TextDocProducer {
	    
	    public CustomTextDocProducer(TextIndex textIndex) { }

        @Override
        public void start() { }

        @Override
        public void finish() { }

        @Override
        public void change(QuadAction qaction, Node g, Node s, Node p, Node o) { }
	}
	
	
	private static class CustomDyadicTextDocProducer implements TextDocProducer {
	    
	    public CustomDyadicTextDocProducer(DatasetGraph dg, TextIndex textIndex) { }

        @Override
        public void start() { }

        @Override
        public void finish() { }

        @Override
        public void change(QuadAction qaction, Node g, Node s, Node p, Node o) { }
	}

}
