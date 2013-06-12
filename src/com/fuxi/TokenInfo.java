package com.fuxi;

/**
 * @author fuxi
 *
 */
public class TokenInfo {
	private String term;
	private Integer term_id;
	private Integer position;
	
	public TokenInfo(String term, Integer term_id, Integer position) {
		this.term = term;
		this.position = position;
		this.term_id = term_id;
	}
	
	public String getTerm() {
		return term;
	}

	public void setTerm(String term) {
		this.term = term;
	}	

	public Integer getTermId() {
		return term_id;
	}

	public void setTermId(Integer term_id) {
		this.term_id = term_id;
	}

	public Integer getPosition() {
		return position;
	}

	public void setPosition(Integer position) {
		this.position = position;
	}

	public TokenInfo copy_some(Boolean c_term, Boolean c_term_id, Boolean c_position) {
        TokenInfo n_token = new TokenInfo(term, term_id, position);
        
        if (!c_term) {
            n_token.setTerm(null);
        }
        
        if (!c_term_id) {
            n_token.setTermId(null);
        }
        
        if (!c_position) {
            n_token.setPosition(null);
        }
        
        return n_token;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		}
		
		Boolean term_eq = true;
		if (term != null && ((TokenInfo)obj).term != null)
		{
			term_eq = term.equals(((TokenInfo)obj).term);
		}

		Boolean term_id_eq = true;
		if (term_id != null && ((TokenInfo)obj).term_id != null)
		{
			term_id_eq = term_id.equals(((TokenInfo)obj).term_id);
		}
		
		Boolean pos_eq = true;
		if (position != null && ((TokenInfo)obj).position != null)
		{
			pos_eq = position.equals(((TokenInfo)obj).position);
		}
		
		return term_eq && term_id_eq && pos_eq;
	};
	
	@Override
	public String toString() {
		return getDebugInfo();
	}
    
    public String getDebugInfo() {
        String info = "";
        
        if (term != null) {
            info += term;
        } else {
            info += "null";
        }
        
        info += " (id: ";
        
        if (term_id != null) {
            info += term_id.toString();
        } else {
            info += "null";
        }
        
        info += ", ";
        
        if (position != null) {
            info += position.toString();
        } else {
            info += "null";
        }
        
        info += ")";
        
        return info;
    }
}
