package com.fuxi;

import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private static String func_name = "mtokens";
	
	protected final String field;
	protected final ValueSource qterms_source;
	protected final ResultType result_type;
	protected final MatchingType matching_type;
	protected final SolrIndexSearcher searcher;
    protected final int filters_no;

	public MatchingTokensFunction(String field, ValueSource qterms_source, ResultType result_type, MatchingType matching_type, SolrIndexSearcher searcher, int filters_no) {
	    this.field = field;
	    this.qterms_source = qterms_source;
	    this.result_type = result_type;
	    this.searcher = searcher;
	    this.matching_type = matching_type;
        this.filters_no = filters_no;
	}
	
	@Override
	public String description() {
		return func_name + "()";
	}

	@Override
	public boolean equals(Object o) {
		return o != null && 
			   o.getClass() == MatchingTokensFunction.class &&
			   this.field.equals(((MatchingTokensFunction)o).field) &&
			   this.qterms_source.equals(((MatchingTokensFunction)o).qterms_source) &&
			   this.result_type.equals(((MatchingTokensFunction)o).result_type) &&
			   this.matching_type.equals(((MatchingTokensFunction)o).matching_type);
	}
	
	public static List<TokenInfo> getTokenizedTerms(TokenizerChain analyzer, String field, String terms, int filters_no) throws IOException {
		// Create analyzer with some of filters to get tokens

		List<TokenInfo> tokens = new ArrayList<TokenInfo>();
		if (terms == null) {
			return tokens;
		}

		TokenFilterFactory[] filters = null;
        if (filters_no == 0) {
            filters = new TokenFilterFactory[0];
        } else if (filters_no == -1) {
            filters = analyzer.getTokenFilterFactories();
        } else {
            filters = Arrays.copyOfRange(analyzer.getTokenFilterFactories(), 0, filters_no);
        }
        
		TokenizerChain token_analyzer = new TokenizerChain(analyzer.getCharFilterFactories(),
														   analyzer.getTokenizerFactory(),
														   filters);
		
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
	
	public static LinkedHashMap<TokenInfo, List<String>> getTokens(TokenizerChain analyzer, String field, String terms, int filters_no) throws IOException {
		List<TokenInfo> tokenized_terms = getTokenizedTerms(analyzer, field, terms, filters_no);
		
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
        final FunctionValues qterms_func_val = qterms_source.getValues(context, readerContext);
        
        
        final FieldType field_type = searcher.getCore().getSchema().getField(field).getType();
        final TokenizerChain qanalyzer = (TokenizerChain)field_type.getQueryAnalyzer();
        
        String qterms = qterms_func_val.strVal(0);
        
        final LinkedHashMap<TokenInfo, List<String>> qTokens = getTokens(qanalyzer, field, qterms, filters_no);
        
		return new StrDocValues(this) {
			
			@Override
			public String strVal(int doc) {
                long estimatedTime = -1;
                long startTime = System.nanoTime();
                
				List<TokenInfo> matching_terms = new ArrayList<TokenInfo>();
                Integer tokenized_field_terms_count = null;
                
				try {
                    Document document = searcher.doc(doc);
                    
                    if (document.get(field) == null) {
                        return null;
                    }
                    
					Analyzer analyzer = field_type.getAnalyzer();
					
					if (!field_type.isTokenized()) {
						LOGGER.error("Field " + field + " has unsupported type. Field has to specify an analyzer.");
						return null;
					}
                    
					TokenStream ts = TokenSources.getAnyTokenStream(topReader, doc, field, analyzer);
					
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
					
                    /* Try to load terms count from Solr's Cache */
                    tokenized_field_terms_count = (Integer)searcher.cacheLookup(func_name, doc + field + filters_no);
                    if (tokenized_field_terms_count == null) {
                        List<TokenInfo> tokenized_field_terms = getTokenizedTerms((TokenizerChain)analyzer, field, document.get(field), filters_no);
                        if (tokenized_field_terms != null) {
                            tokenized_field_terms_count = tokenized_field_terms.size();
                            searcher.cacheInsert(func_name, doc + field + filters_no, tokenized_field_terms_count);
                        } else {
                            LOGGER.error("There was a problem with field " + field + ". Could not tokenize field value! (not stored?)");
                            return null;
                        }
                    }
					
					ts.reset();
					
					int token_position = 0;
					while (ts.incrementToken()) {
					    String term = charTermAttribute.toString();
					    token_position += posIncAttribute.getPositionIncrement();
					    
					    for (Map.Entry<TokenInfo, List<String>> qTokenEntry : qTokens.entrySet()) {
							TokenInfo token_info = qTokenEntry.getKey();
							List<String> qAnalyzedTokens = qTokenEntry.getValue();
							
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

                List<TokenInfo> sorted_matching_terms = new ArrayList<TokenInfo>();
				
				// Synchronise order
				int loop_no = qTokens.entrySet().size();
				
				for (Map.Entry<TokenInfo, List<String>> qTokenEntry : qTokens.entrySet()) {
					TokenInfo token_info = qTokenEntry.getKey();

					--loop_no;

					Boolean trigger_group = false;
                    
                    if (matching_terms.contains(token_info) && (token_info.getPosition() != null)) {
                        Boolean add_term = false;
                        
                        if (matching_type == MatchingType.full || matching_type == MatchingType.full_ordered) {
                            if (!sorted_matching_terms.contains(token_info.copy_some(false, false, true))) {
                                add_term = true;
                            } else if (sorted_matching_terms.size() != tokenized_field_terms_count) {
                                sorted_matching_terms.clear();
                                add_term = true;
                            }
                            
                            if (matching_type == MatchingType.full_ordered &&
                                       !sorted_matching_terms.isEmpty() &&
                                       token_info.getPosition() <= sorted_matching_terms.get(sorted_matching_terms.size()-1).getPosition()) {
                                sorted_matching_terms.clear();
                                add_term = true;
                            }
                            
                        } else {
                            add_term = true;
                        }
                        
                        if (add_term) {
                            if (result_type == ResultType.all) {
                                LOGGER.warn("Doc: " + doc + ". Add as match: " + token_info + ". Key: " + token_info.copy_some(false, false, true));
                            }
                            sorted_matching_terms.add(token_info);
                        } else {
                            if (result_type == ResultType.all) {
                                LOGGER.warn("Doc: " + doc + ". Will not add: " + token_info + ". Key: " + token_info.copy_some(false, false, true));
                            }
                        }
                        
                        if ((matching_type == MatchingType.full || matching_type == MatchingType.full_ordered) &&
                            sorted_matching_terms.size() == tokenized_field_terms_count) {
                            trigger_group = true;
                        }
					}
					
					if (!matching_terms.contains(token_info) || trigger_group == true || loop_no == 0) {
						if (matching_type == MatchingType.full) {
							if (sorted_matching_terms.size() < tokenized_field_terms_count) {
                                if (result_type == ResultType.all) {
                                    LOGGER.warn("Too little");
                                }

								sorted_matching_terms.clear();
								continue;
                            }
						}
						
						List<String> token_repr = new ArrayList<String>();
						
						for (TokenInfo ti: sorted_matching_terms) {
                            switch (result_type) {
                                case id:
                                    token_repr.add(ti.getTermId().toString());
                                    break;
        
                                case term:
                                    token_repr.add(ti.getTerm());
                                    break;
                                
                                case all:
                                    token_repr.add(ti.getDebugInfo());
                                    break;
                                
                                default:
                                    LOGGER.error("Result type unsuported: " + result_type.toString());
                                    return null;
                            }
						}
						
						String key = StringUtils.join(token_repr, " ");
						str_values.add(key);

                        if (token_repr.size() > longest_count) {
                            longest_count = token_repr.size();
							entry_ids.clear();
							entry_ids.add(str_values.size()-1);
                        } else if (token_repr.size() == longest_count) {
							entry_ids.add(str_values.size()-1);
						}
                        
						sorted_matching_terms.clear();
                        token_repr.clear();
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
                
                estimatedTime = System.nanoTime() - startTime;
                //LOGGER.warn("estimatedTime: " + estimatedTime/1000000.0 + "ms");

				return StringUtils.join(top_str_values, " | ");
			}
			
		};
	}

	
	private static final int hcode = MatchingTokensFunction.class.hashCode();

	@Override
	public int hashCode() {
		return hcode + field.hashCode() + qterms_source.hashCode() + result_type.hashCode() + matching_type.hashCode();
	}

}
