package com.fuxi;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.util.TokenFilterFactory;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.ReaderUtil;
import org.apache.lucene.queries.function.FunctionValues;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.queries.function.docvalues.StrDocValues;
import org.apache.lucene.search.highlight.TokenSources;
import org.apache.solr.analysis.TokenizerChain;
import org.apache.solr.search.SolrIndexSearcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MatchingTokensFunction extends ValueSource {
	private static Logger LOGGER = LoggerFactory.getLogger(MatchingTokensFunction.class);
	
	protected final String field;
	protected final String qterms;

	public MatchingTokensFunction(String field, String qterms) {
	    this.field = field;
	    this.qterms = qterms;
	}
	
	@Override
	public String description() {
		return "mtokens()";
	}

	@Override
	public boolean equals(Object o) {
		return o != null && o.getClass() == MatchingTokensFunction.class && this.field.equals(((MatchingTokensFunction)o).field) && this.qterms.equals(((MatchingTokensFunction)o).qterms);
	}
	
	public static LinkedHashMap<String, List<String>> getTokens(TokenizerChain analyzer, String field, String terms) throws IOException {
		// Analyzer without filters to get tokens
		
		TokenFilterFactory[] filters = new TokenFilterFactory[0];
		TokenizerChain token_analyzer = new TokenizerChain(analyzer.getCharFilterFactories(),
														   analyzer.getTokenizerFactory(),
														   filters);
		
		StringReader qReader = new StringReader(terms);
		TokenStream qts = token_analyzer.tokenStream(field, qReader);
		CharTermAttribute qcterm = qts.addAttribute(CharTermAttribute.class);

		LinkedHashMap<String, List<String>> tokens = new LinkedHashMap<String, List<String>>();
		
		qts.reset();
		
		while (qts.incrementToken()) {
			String term = qcterm.toString(); 
			
			StringReader tReader = new StringReader(term);
			
			TokenStream qtokens = analyzer.tokenStream(field, tReader);
			CharTermAttribute term_token = qtokens.addAttribute(CharTermAttribute.class);
			qtokens.reset();
			
			List<String> term_token_lst = new ArrayList<String>();
			
			while (qtokens.incrementToken()) {
				term_token_lst.add(term_token.toString());
			}
			
			tokens.put(term, term_token_lst);
			qtokens.close();
		}
		
		qts.close();
		token_analyzer.close();
		
		return tokens;
	}

	@Override
	public FunctionValues getValues(Map context, AtomicReaderContext readerContext) throws IOException {
		final IndexReader topReader = ReaderUtil.getTopLevelContext(readerContext).reader();
		final SolrIndexSearcher searcher = (SolrIndexSearcher)context.get("searcher");
	    
		return new StrDocValues(this) {
			
			@Override
			public String strVal(int doc) {
				List<String> matching_terms = new ArrayList<String>();
				LinkedHashMap<String, List<String>> qTokens = null;
				
				try {
					Analyzer analyzer = searcher.getCore().getSchema().getField(field).getType().getAnalyzer();
					TokenizerChain qanalyzer = (TokenizerChain)searcher.getCore().getSchema().getField(field).getType().getQueryAnalyzer();
					IndexableField idx_filed = searcher.doc(doc).getField(field);
					String f_value = idx_filed.stringValue();
					
					TokenStream ts = TokenSources.getAnyTokenStream(topReader, doc, field, analyzer);
					//OffsetAttribute offsetAttribute = ts.addAttribute(OffsetAttribute.class);
					CharTermAttribute charTermAttribute = ts.addAttribute(CharTermAttribute.class);
					
					qTokens = getTokens(qanalyzer, field, qterms);
					
					ts.reset();
					
					while (ts.incrementToken()) {
					    //int startOffset = offsetAttribute.startOffset();
					    //int endOffset = offsetAttribute.endOffset();
					    String term = charTermAttribute.toString();
					    
					    for (Map.Entry<String, List<String>> qTokenEntry : qTokens.entrySet()) {
							String qTerm = qTokenEntry.getKey();
							List<String> qAnalyzedTokens = qTokenEntry.getValue();
							
							if (!matching_terms.contains(qTerm) && qAnalyzedTokens.contains(term) ) {
								matching_terms.add(qTerm);
							}
						}
					}
					
					ts.close();
					
				} catch (IOException e) {
					e.printStackTrace();
					return null;
				}
				
				if (matching_terms.isEmpty())
				{
					return null;
				}

				int longest_count = 0;
				List<Integer> entry_ids = new ArrayList<Integer>();
				
				List<String> str_values = new ArrayList<String>();

				List<String> sorted_matching_terms = new ArrayList<String>();
				
				// Synchronise order
				int counter = 0;
				int loop_no = qTokens.entrySet().size();
				
				for (Map.Entry<String, List<String>> qTokenEntry : qTokens.entrySet()) {
					String key = qTokenEntry.getKey();

					--loop_no;
					
					if (matching_terms.contains(key)) {
						sorted_matching_terms.add(key);
						counter++;
					} 
					
					if (!matching_terms.contains(key) | loop_no == 0) {
						key = StringUtils.join(sorted_matching_terms, " ");
						str_values.add(key);
						sorted_matching_terms.clear();

						if (counter > longest_count) {
							longest_count = counter;
							entry_ids.clear();
							entry_ids.add(str_values.size()-1);
						} else if (counter == longest_count) {
							entry_ids.add(str_values.size()-1);
						}
						counter = 0;

					}
				}
				
				List<String> top_str_values = new ArrayList<String>();
				
				for (int entry_id: entry_ids) {
					top_str_values.add(str_values.get(entry_id));
				}

				return StringUtils.join(top_str_values, " | ");
			}
			
		};
	}

	
	private static final int hcode = MatchingTokensFunction.class.hashCode();

	@Override
	public int hashCode() {
		return hcode + field.hashCode() + qterms.hashCode();
	}

}
