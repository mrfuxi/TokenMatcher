package com.fuxi;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.util.TokenFilterFactory;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.ReaderUtil;
import org.apache.lucene.queries.function.FunctionValues;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.queries.function.docvalues.StrDocValues;
import org.apache.lucene.search.highlight.TokenSources;
import org.apache.solr.analysis.TokenizerChain;
import org.apache.solr.schema.FieldType;
import org.apache.solr.search.SolrIndexSearcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MatchingTokensFunction extends ValueSource {
	public enum ResultType {
		term, id, all
	}

	public enum MatchingType {
		simple, full, full_ordered
	}
	
	private static Logger LOGGER = LoggerFactory.getLogger(MatchingTokensFunction.class);
	
	protected final String field;
	protected final String qterms;
	protected final ResultType result_type;
	protected final MatchingType matching_type;
	protected final SolrIndexSearcher searcher;

	public MatchingTokensFunction(String field, String qterms, ResultType result_type, MatchingType matching_type, SolrIndexSearcher searcher) {
	    this.field = field;
	    this.qterms = qterms;
	    this.result_type = result_type;
	    this.searcher = searcher;
	    this.matching_type = matching_type;
	}
	
	@Override
	public String description() {
		return "mtokens()";
	}

	@Override
	public boolean equals(Object o) {
		return o != null && 
			   o.getClass() == MatchingTokensFunction.class &&
			   this.field.equals(((MatchingTokensFunction)o).field) &&
			   this.qterms.equals(((MatchingTokensFunction)o).qterms) &&
			   this.result_type.equals(((MatchingTokensFunction)o).result_type) &&
			   this.matching_type.equals(((MatchingTokensFunction)o).matching_type);
	}
	
	public static List<TokenInfo> getTokenizedTerms(TokenizerChain analyzer, String field, String terms) throws IOException {
		// Create analyzer without filters to get tokens

		List<TokenInfo> tokens = new ArrayList<TokenInfo>();
		if (terms == null) {
			return tokens;
		}
		
		TokenFilterFactory[] empty_filters = new TokenFilterFactory[0];
		TokenizerChain token_analyzer = new TokenizerChain(analyzer.getCharFilterFactories(),
														   analyzer.getTokenizerFactory(),
														   empty_filters);
		
		StringReader qReader = new StringReader(terms);
		TokenStream qts = token_analyzer.tokenStream(field, qReader);
		CharTermAttribute qcterm = qts.addAttribute(CharTermAttribute.class);
		
		qts.reset();
		
		int term_id = 0;
		while (qts.incrementToken()) {
			TokenInfo token = new TokenInfo(qcterm.toString(), term_id, null);
			tokens.add(token);
			term_id++;
		}
		
		qts.close();
		token_analyzer.close();
		
		return tokens;
	}
	
	public static LinkedHashMap<TokenInfo, List<String>> getTokens(TokenizerChain analyzer, String field, String terms) throws IOException {
		List<TokenInfo> tokenized_terms = getTokenizedTerms(analyzer, field, terms);
		
		LinkedHashMap<TokenInfo, List<String>> tokens = new LinkedHashMap<TokenInfo, List<String>>();
		 
		for (TokenInfo term : tokenized_terms) {
			StringReader tReader = new StringReader(term.getTerm());
			
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
		
		return tokens;
	}

	@Override
	public FunctionValues getValues(Map context, AtomicReaderContext readerContext) throws IOException {
		final IndexReader topReader = ReaderUtil.getTopLevelContext(readerContext).reader();
	    
		return new StrDocValues(this) {
			
			@Override
			public String strVal(int doc) {
				List<TokenInfo> matching_terms = new ArrayList<TokenInfo>();
				LinkedHashMap<TokenInfo, List<String>> qTokens = null;
				List<TokenInfo> tokenized_field_terms = null;
				
				try {
					FieldType field_type = searcher.getCore().getSchema().getField(field).getType(); 
					Analyzer analyzer = field_type.getAnalyzer();
					
					if (!field_type.isTokenized()) {
						LOGGER.error("Field " + field + " has unsupported type. Field has to specify an analyzer.");
						return null;
					}
											
					Document document = searcher.doc(doc);
					TokenStream ts = TokenSources.getAnyTokenStream(topReader, doc, field, document, analyzer);					
					
					CharTermAttribute charTermAttribute = null;
					PositionIncrementAttribute posIncAttribute = null;
										
					try {
						charTermAttribute = ts.addAttribute(CharTermAttribute.class);
					} catch (IllegalArgumentException e) {
						LOGGER.error("There was a problem with field " + field + ". Text term could not be extracted, due to field type.");
						return null;
					}

					try {
						posIncAttribute = ts.addAttribute(PositionIncrementAttribute.class);
					} catch (IllegalArgumentException e) {
						LOGGER.error("There was a problem with field " + field + ". Text term's position could not be extracted, due to field type.");
						return null;
					}

					
					TokenizerChain qanalyzer = (TokenizerChain)field_type.getQueryAnalyzer();					
					qTokens = getTokens(qanalyzer, field, qterms);
					
					tokenized_field_terms = getTokenizedTerms((TokenizerChain)analyzer, field, document.get(field));
					
					ts.reset();
					
					int token_position = 0;
					while (ts.incrementToken()) {
					    String term = charTermAttribute.toString();
					    token_position += posIncAttribute.getPositionIncrement();
					    
					    for (Map.Entry<TokenInfo, List<String>> qTokenEntry : qTokens.entrySet()) {
							TokenInfo token_info = qTokenEntry.getKey();
							List<String> qAnalyzedTokens = qTokenEntry.getValue();
							
							/*
							 * FIXME: Check existence of term also based on it's position! DONE?
							 */
							if (!matching_terms.contains(token_info) && qAnalyzedTokens.contains(term) ) {
								token_info.setPosition(token_position);
								matching_terms.add(token_info);
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
				
				for (Map.Entry<TokenInfo, List<String>> qTokenEntry : qTokens.entrySet()) {
					TokenInfo token_info = qTokenEntry.getKey();

					--loop_no;
					
					if (matching_terms.contains(token_info)) {
						switch (result_type) {
							case id:
								sorted_matching_terms.add(token_info.getTermId().toString());
								break;
	
							case term:
								sorted_matching_terms.add(token_info.getTerm());
								break;
							
							case all:
								sorted_matching_terms.add(token_info.getTerm() + "(" + token_info.getTermId().toString() + " - " + token_info.getPosition().toString() + ")");
								break;
							
							default:
								LOGGER.error("Result type unsuported: " + result_type.toString());
								return null;
						}
							
						counter++;
					}
					
					if (!matching_terms.contains(token_info) | loop_no == 0) {
						if (matching_type == MatchingType.full)
						{
							/*
							 * FIXME: Get correct counts based on unique matches!. To do that, convert tokens to strings much later
							Set<Integer> positions = new HashSet<Integer>();
							
							for (TokenInfo ti: sorted_matching_terms) {
								if (ti.getPosition() != null) {
									
								}
							}
							*/
							
							if (sorted_matching_terms.size() < tokenized_field_terms.size()) {
								sorted_matching_terms.clear();
								counter = 0;
								//return "too little";
								continue;
							} else if (sorted_matching_terms.size() > tokenized_field_terms.size()) {
								// FIXME: produce results when there are terms that matched more than 1 time
								/*
								sorted_matching_terms.clear();
								counter = 0;
								continue;
								//return "too much";
								*/
							}
						}
						
						String key = StringUtils.join(sorted_matching_terms, " ");						
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
				
				if (entry_ids.isEmpty())
				{
					return null;
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
		return hcode + field.hashCode() + qterms.hashCode() + result_type.hashCode() + matching_type.hashCode();
	}

}
