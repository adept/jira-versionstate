/*
 * Copyright (c) 2010 Dmitry Astapov <dastapov@gmail.com>
 * Distributed under BSD License
 */
package ua.astapov.jira.plugins.versionstate;

import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

import org.apache.log4j.Logger;

import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.context.JiraContextNode;
import com.atlassian.jira.issue.customfields.MultipleCustomFieldType;
import com.atlassian.jira.issue.customfields.MultipleSettableCustomFieldType;
import com.atlassian.jira.issue.customfields.SortableCustomField;
import com.atlassian.jira.issue.customfields.config.item.SettableOptionsConfigItem;
import com.atlassian.jira.issue.customfields.impl.CalculatedCFType;
import com.atlassian.jira.issue.customfields.impl.FieldValidationException;
import com.atlassian.jira.issue.customfields.manager.OptionsManager;
import com.atlassian.jira.issue.customfields.option.Option;
import com.atlassian.jira.issue.customfields.option.Options;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.fields.config.FieldConfig;
import com.atlassian.jira.issue.search.SearchException;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.version.Version;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.util.EasyList;
import com.atlassian.jira.web.bean.PagerFilter;
@SuppressWarnings({ "unchecked", "rawtypes" })
public class VersionStateCFType extends CalculatedCFType implements SortableCustomField, MultipleCustomFieldType, MultipleSettableCustomFieldType 
{
    private static final Logger log = Logger.getLogger(VersionStateCFType.class);

	private final OptionsManager optionsManager;
    private final SearchService searchService;
    private final JiraAuthenticationContext authenticationContext;

    public VersionStateCFType(OptionsManager optionsManager, SearchService searchService, JiraAuthenticationContext authenticationContext)
    {
        this.searchService = searchService;
        this.authenticationContext = authenticationContext;
		this.optionsManager = optionsManager;
    }

    public String getStringFromSingularObject(Object value)
    {
        return value != null ? value.toString() : Boolean.FALSE.toString();
    }

    public Object getSingularObjectFromString(String string) throws FieldValidationException
    {
        if (string != null)
        {
            return (string);
        }
        else
        {
            return Boolean.FALSE.toString();
        }
    }

    public Object getValueFromIssue(CustomField field, Issue issue)
    {
    	// Get a list of stored options for the field
    	List<Option> options = optionsManager.getOptions(field.getRelevantConfig(issue)).getRootOptions();
        if (options.size() != 1) 
        {
          return "Please create a single configuration option for this field. Value should be the prefix of the name of the version that you want to monitor";
        }
        
    	String linkTypeName = ((Option) options.get(0)).getValue();
        log.debug("linkTypeName: " + linkTypeName);
        
        String versionState="";
        
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0); // 24-hour clock, so - not "HOUR"
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        Date today = calendar.getTime();
        
        final Project theProject = issue.getProjectObject();
        
        // Inspecting all Versions in theProject
        for (Iterator<Version> allVersions = theProject.getVersions().iterator(); allVersions.hasNext();)
        {
        	final Version ver = allVersions.next();
        	if (ver.isReleased()) {
        		// Version is released, everything is OK
        		versionState="<table><tr><td bgcolor=\"#00ff00\">Released</td></tr></table>";
        	} else {
        		final Date relDate = ver.getReleaseDate();
        		if (relDate == null) {
        		   // Version is not dated, leave empty value
        		   // This should not happen. 
        		} else {
        			if (relDate.before(today)) {
        				// Version is overdue
        				versionState="<table><tr><td bgcolor=\"#ff0000\">Delayed</td></tr></table>";
        			} else {
        				// Version is on time, have to check tasks
        				final String verName = ver.getName();
        				final List<Issue> overdue = getOverdueIssues(issue,verName);
        				if (overdue.isEmpty()) {
        					versionState="<table><tr><td bgcolor=\"#00ff00\">On time</td></tr></table>";
        				} else {
        					versionState="<table><tr><td bgcolor=\"#00ffff\">Lagging</td></tr></table>";
        				}
        			}
        		}
        	}
        }
        return versionState;

    }
    
    // Return issues in the current project not matching the current issue type, affected by given
    // version and overdue
    private List<Issue> getOverdueIssues(Issue issue, String verName) {
        final String projectKey = issue.getProjectObject().getKey();
        final String issueType = issue.getIssueTypeObject().getName();
        // Excluding MTS Project by type
        String jqlQuery = "project = " + projectKey + " and issuetype != \"" + issueType + "\" and affectedVersion = \"" + verName+ "\" and due <= -1d ORDER BY key DESC";

        final SearchService.ParseResult parseResult = searchService.parseQuery(authenticationContext.getUser(), jqlQuery);

        if (parseResult.isValid())
        {
            try
            {
                final SearchResults results = searchService.search(authenticationContext.getUser(),
                        parseResult.getQuery(), PagerFilter.getUnlimitedFilter());
                final List<Issue> issues = results.getIssues();
                return issues;
            }
            catch (SearchException e)
            {
                log.error("Error running search", e);
                return new LinkedList<Issue> ();
            }
        }
        else
        {
            log.error("Error parsing jqlQuery: " + parseResult.getErrors());
            return new LinkedList<Issue> ();
        }    	
    }
    
    
    public List getConfigurationItemTypes()
    {
    	return EasyList.build(new SettableOptionsConfigItem(this, optionsManager));
    }
    
    public Options getOptions(FieldConfig config, JiraContextNode jiraContextNode)
    {
    	return this.optionsManager.getOptions(config);
    }

	public Set getIssueIdsWithValue(CustomField arg0, Option arg1) {
		// TODO Auto-generated method stub
		return new HashSet();
	}

	public void removeValue(CustomField arg0, Issue arg1, Option arg2) {
		// TODO Auto-generated method stub
	}
}
