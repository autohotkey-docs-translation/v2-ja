/* 
 * QA script
 *
 * Based on check_rules.groovy by
 * Briac Pilpre, Piotr Kulik, Kos Ivantsov, and Didier Briel
 *
 * Revised to support Japanese language
 */

// if FALSE only current file will be checked
checkWholeProject = true
// 0 - segment number, 1 - rule, 2 - source, 3 - target
defaultSortColumn = 1
// if TRUE column will be sorted in reverse
defaultSortOrderDescending = false

import groovy.swing.SwingBuilder
import groovy.beans.Bindable
import javax.swing.JButton
import javax.swing.JTable
import javax.swing.table.*
import javax.swing.event.*
import javax.swing.RowSorter.SortKey
import javax.swing.RowSorter
import javax.swing.SortOrder
import static javax.swing.JOptionPane.*
import java.awt.Component
import java.awt.Point
import java.awt.Rectangle
import java.awt.Dimension
import java.awt.event.*
import java.awt.BorderLayout as BL
import java.awt.GridBagConstraints
import org.omegat.core.Core
import org.omegat.tokenizer.ITokenizer.StemmingMode
import org.omegat.languagetools.LanguageToolNativeBridge

import org.languagetool.JLanguageTool;
import org.languagetool.Language;
import org.languagetool.rules.RuleMatch;
import org.languagetool.rules.bitext.BitextRule;
import org.languagetool.rules.bitext.DifferentLengthRule;
import org.languagetool.rules.bitext.SameTranslationRule;
import org.languagetool.tools.Tools;

class QACheckData {
    @Bindable data = []
}
public class IntegerComparator implements Comparator<Integer> {
    public int compare(Integer o1, Integer o2) {
        return o1 - o2
    }
}

def checker = Core.getSpellChecker()
def tokenizer = project.getTargetTokenizer()

swing = new SwingBuilder()
bRules = null;

sourceLang = getLTLanguage(project.getProjectProperties().getSourceLanguage());
targetLang = getLTLanguage(project.getProjectProperties().getTargetLanguage());

sourceLt = null
targetLt = null
if (sourceLang != null) {
	sourceLt = getLanguageToolInstance(sourceLang)
}
if (targetLang != null) {
	targetLt = getLanguageToolInstance(targetLang)
}
if (sourceLt != null && targetLt != null) {
    bRules = getBiTextRules(sourceLang, targetLang);
}

checkLanguageTools = false
nameLanguageTools = "LanguageTools Rules" // res.getString("nameLanguageTools")
checkLeadSpace = false
nameLeadSpace = "Whitespace - leading" // res.getString("nameLeadSpace")
checkTrailSpace = false
nameTrailSpace = "Whitespace - trailing" // res.getString("nameTrailSpace")
checkDoubleSpace = false
nameDoubleSpace = "Doubled blanks" // res.getString("nameDoubleSpace")
checkDoubleWords = false
nameDoubleWords = "Doubled words" // res.getString("nameDoubleWords")
checkTargetShorter = false
nameTargetShorter = "Target shorter" // res.getString("nameTargetShorter")
checkTargetLonger = false
nameTargetLonger = "Target longer" // res.getString("nameTargetLonger")
checkDiffPunctuation = true
nameDiffPunctuation = "Different punctuation" // res.getString("nameDiffPunctuation")
checkDiffStartCase = false
nameDiffStartCase = "Different start case" // res.getString("nameDiffStartCase")
checkEqualSourceTarget = false
nameEqualSourceTarget = "Equal source & target" // res.getString("nameEqualSourceTarget")
checkUntranslated = false
nameUntranslated = "Untranslated segment" // res.getString("nameUntranslated")
checkTagNumber = false
nameTagNumber = "Tags - number" // res.getString("nameTagNumber")
checkTagSpace = false
nameTagSpace = "Tags - spaces" // res.getString("nameTagSpace")
checkTagOrder = false
nameTagOrder = "Tags - order" // res.getString("nameTagOrder")
checkNumErr = false
nameNumErr = "Inconsistent numbers" // res.getString("nameNumErr")
checkSpellErr = false
nameSpellErr = "Spelling errors(s)" // res.getString("nameSpellErr")


/*
 The rules are based on the Checkmate Quality check 
 http://www.opentag.com/okapi/wiki/index.php?title=CheckMate_-_Quality_Check_Configuration
 Each rule is a block of groovy code, 'source' and 'target' are the two parameters of this block
 */
ruleset = [

        // Spaces verification
            (nameLeadSpace): { s, t ->  t =~ /^\s+/ },
            (nameTrailSpace): { s, t -> t =~ /\s+$/ },
            (nameDoubleSpace): { s, t -> t =~ /[\s\u00A0]{2}/ },
        // Segment verification
            (nameDoubleWords): { s, t -> t =~ /(?i)(\b\w+)\s+\1\b/ },
        // Length
            (nameTargetShorter): { s, t -> if (t != QA_empty){(t.length() / s.length() * 100) < minCharLengthAbove}
                },
            (nameTargetLonger): { s, t -> if (t != QA_empty){(t.length() / s.length() * 100) > maxCharLengthAbove}
                },
        // Punctuation
            (nameDiffPunctuation): { s, t -> if (t != QA_empty){def s1 = s[-1], t1 = t[-1];
                // japanese
                t1 = (t1 == '。') ? '.' : t1;
                t1 = (t1 == '：') ? ':' : t1;
                t1 = (t1 == '？') ? '?' : t1;
                t1 = (t1 == '！') ? '!' : t1;
                // --
                '.!?;:'.contains(s1) ? s1 != t1 : '.!?;:'.contains(t1)}
                },
        // Case of first letter in segment
            (nameDiffStartCase): { s, t -> if (t != QA_empty){def s1 = s[0] =~ /^\p{Lu}/ ? 'up' : 'low'
                t1 = t[0] =~ /^\p{Lu}/ ? 'up' : 'low'
                s1 != t1 }
                },
        // Source = Target
            (nameEqualSourceTarget): { s, t -> t == s },
        // Untranslated
            (nameUntranslated): { s, t -> t == QA_empty },
        // Tag Errors
            (nameTagNumber): { s, t -> if (t != QA_empty){def tt = t.findAll(/<\/?[a-z]+[0-9]* ?\/?>/), 
                st = s.findAll(/<\/?[a-z]+[0-9]* ?\/?>/)
                st.size() != tt.size() }
                },
            (nameTagSpace): { s, t -> if (t != QA_empty){def tt = t.findAll(/\s?<\/?[a-z]+[0-9]* ?\/?>\s?/),
                st = s.findAll(/\s?<\/?[a-z]+[0-9]* ?\/?>\s?/)
                    if (st.size() == tt.size())
                    st.sort() != tt.sort()
                    }
                },
            (nameTagOrder): { s, t -> if (t != QA_empty){def tt = t.findAll(/<\/?[a-z]+[0-9]* ?\/?>/),
                st = s.findAll(/<\/?[a-z]+[0-9]* ?\/?>/)
                    if (st.size() == tt.size())
                    st != tt
                    }
                },
            (nameNumErr): {s, t -> if (t != QA_empty){def tt = t.replaceAll(/<\/?[a-z]+[0-9]* ?\/?>/, ''),
                st = s.replaceAll(/<\/?[a-z]+[0-9]* ?\/?>/, ''),
                tn = tt.findAll(/\d+/), sn = st.findAll(/\d+/)
                    sn != tn
                    }
                },
            (nameSpellErr): {s, t -> if (t != QA_empty) {
                def spellerror = []
                tokenizer.tokenizeWords(t, StemmingMode.NONE).each {
                    def (int a, int b) = [it.offset, it.offset + it.length]
                    def word = t.substring( a, b )
                        if (!checker.isCorrect(word)) {
                            spellerror.add([word])
                        }
                    }
                spellerror.size()
                }}

            ]

def segment_count

def prop = project.projectProperties
if (!prop) {
    final def title = "Check Rules" // res.getString("title")
    final def msg   = "Please try again after you open a project." // res.getString("noProjMsg")
    console.clear()
    console.println(title + "\n${"-"*15}\n" + msg)
    showMessageDialog null, msg, title, INFORMATION_MESSAGE
    return
}

def QAcheck() {
    rules = ruleset.clone()
// Prefs
    maxCharLengthAbove=240
    minCharLengthAbove=40
    QA_empty = ''
// Run with preferred rules
    if (!checkLeadSpace) {
        rules.remove(nameLeadSpace)
    }
    if (!checkTrailSpace) {
        rules.remove(nameTrailSpace)
    }
    if (!checkDoubleSpace) {
        rules.remove(nameDoubleSpace)
    }
    if (!checkDoubleWords) {
        rules.remove(nameDoubleWords)
    }
    if (!checkTargetShorter) {
        rules.remove(nameTargetShorter)
    }
    if (!checkTargetLonger) {
        rules.remove(nameTargetLonger)
    }
    if (!checkDiffPunctuation) {
        rules.remove(nameDiffPunctuation)
    }
    if (!checkDiffStartCase) {
        rules.remove(nameDiffStartCase)
    }
    if (!checkEqualSourceTarget) {
        rules.remove(nameEqualSourceTarget)
    }
    if (!checkUntranslated) {
        rules.remove(nameUntranslated)
    }
    if (!checkTagNumber) {
        rules.remove(nameTagNumber)
    }
    if (!checkTagSpace) {
        rules.remove(nameTagSpace)
    }
    if (!checkTagOrder) {
        rules.remove(nameTagOrder)
    }
    if (!checkNumErr) {
        rules.remove(nameNumErr)
    }
    if (!checkSpellErr) {
        rules.remove(nameSpellErr)
    }



    model = new QACheckData()
    segment_count = 0

    console.clear()
    console.println("Check Rules"+"\n${'-'*15}"); // res.getString("title")
    files = project.projectFiles


    if (!checkWholeProject) {
        files = project.projectFiles.subList(editor.@displayedFileIndex, editor.@displayedFileIndex + 1);
    }

    fileLoop:
    for (i in 0 ..< files.size()) {
        fi = files[i]

        for (j in 0 ..< fi.entries.size()) {
        
            if (java.lang.Thread.interrupted()) {
                break fileLoop;
            }

            ste = fi.entries[j];
            source = ste.getSrcText();
            target = project.getTranslationInfo(ste) ? project.getTranslationInfo(ste).translation : null;

            // Do LanguageTools check
            if (checkLanguageTools) {
                for (RuleMatch rule : getRuleMatchesForEntry(source, target))
                {
                    console.println(ste.entryNum() + "\t" + rule.getMessage() + /*"\t[" + source + "]" + */"\t[" + target + "]");
                    model.data.add([ seg: ste.entryNum(), rule: rule.getMessage(), source: source, target: target ]);
                    segment_count++;
                }
            }

            if ( target == null || target.length() == 0) {
                target = QA_empty
            }

            rules.each { k, v ->
                if (rules[k](source, target)) {
                    console.println(ste.entryNum() + "\t" + k + /*"\t[" + source + "]" + */"\t[" + target + "]");
                    model.data.add([ seg: ste.entryNum(), rule: k, source: source, target: target ]);
                    segment_count++;
                }
            }
        }
    }
    console.print("${'-'*15}\n" + "Errors found:" + segment_count) // res.getString("errors_count")
}


def interfejs(locationxy = new Point(0, 0), width = 900, height = 550, scrollpos = 0, sortColumn = defaultSortColumn, sortOrderDescending = defaultSortOrderDescending) {
    def frame
    frame = swing.frame(title: "Check Rules" + ". " + "Errors found: " + segment_count, minimumSize: [width, height], pack: true, show: true) { // title: res.getString("title") ... getString("errors_count")
        def tab
        def skroll
        skroll = scrollPane {
            tab = table() {
                tableModel(list: model.data) {
                    propertyColumn(editable: true, header:"Segment", propertyName:'seg', minWidth: 80, maxWidth: 80, preferredWidth: 80, // header:res.getString("segment")
                        cellEditor: new TableCellEditor()
                        {
                            public void cancelCellEditing()                             {   }
                            public boolean stopCellEditing()                            {   return false;   }
                            public Object getCellEditorValue()                          {   return value;   }
                            public boolean isCellEditable(EventObject anEvent)          {   return true;    }
                            public boolean shouldSelectCell(EventObject anEvent)        {   return true;   }
                            public void addCellEditorListener(CellEditorListener l)     {}
                            public void removeCellEditorListener(CellEditorListener l)  {}
                            public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column)
                            {
                                org.omegat.core.Core.getEditor().gotoEntry(value);
                            }
                        },
                        cellRenderer: new TableCellRenderer()
                        {
                            public Component getTableCellRendererComponent(JTable table,
                                Object value,
                                boolean isSelected,
                                boolean hasFocus,
                                int row,
                                int column)
                            {
                                def btn = new JButton()
                                btn.setText(value.toString())
                                return btn
                            }
                        }
                    )
                    propertyColumn(editable: false, header:"Rule", propertyName:'rule', minWidth: 120, preferredWidth: 180) // header:res.getString("rule")
                    propertyColumn(editable: false, header:"Target", propertyName:'target', minWidth: 200, preferredWidth: 320) //  header:res.getString("target")
                    propertyColumn(editable: false, header:"Source", propertyName:'source', minWidth: 200, preferredWidth: 320) // header:res.getString("source")
                }
            }
            tab.getTableHeader().setReorderingAllowed(false);
        }
        rowSorter = new TableRowSorter(tab.model);
        rowSorter.setComparator(0, new IntegerComparator());
        sortKeyz = new ArrayList<RowSorter.SortKey>();
        sortKeyz.add(new RowSorter.SortKey(sortColumn, sortOrderDescending ? SortOrder.DESCENDING : SortOrder.ASCENDING));
        rowSorter.setSortKeys(sortKeyz);
        tab.setRowSorter(rowSorter);

        skroll.getVerticalScrollBar().setValue(scrollpos);
        tab.scrollRectToVisible(new Rectangle (0, scrollpos, 1, scrollpos + 1));
        skroll.repaint();
        panel(constraints:BL.SOUTH) {
            gridBagLayout();
            checkBox(text:"Check whole project", // text:res.getString("checkWholeProject")
                selected: checkWholeProject,
                actionPerformed: {
                    checkWholeProject = !checkWholeProject;
                },
                constraints:gbc(gridx:0, gridy:0, weightx: 0.5, fill:GridBagConstraints.HORIZONTAL, insets:[0,5,0,0]))
            checkBox(text:"Check for segments with spelling errors", // text:res.getString("checkSpellErr")
                selected: checkSpellErr,
                actionPerformed: {
                    checkSpellErr = !checkSpellErr;
                },
                constraints:gbc(gridx:0, gridy:1, weightx: 0.5, fill:GridBagConstraints.HORIZONTAL, insets:[0,5,0,0]))
            checkBox(text:"Check LanguageTools rules", // text:res.getString("checkLanguageTools")
                selected: checkLanguageTools,
                actionPerformed: {
                    checkLanguageTools = !checkLanguageTools;
                },
                constraints:gbc(gridx:0, gridy:2, weightx: 0.5, fill:GridBagConstraints.HORIZONTAL, insets:[0,5,0,0]))


            checkBox(text:"Check for leading whitespace", // text:res.getString("checkLeadSpace")
                selected: checkLeadSpace,
                actionPerformed: {
                    checkLeadSpace = !checkLeadSpace;
                },
                constraints:gbc(gridx:1, gridy:0, weightx: 0.5, fill:GridBagConstraints.HORIZONTAL, insets:[0,5,0,0]))
            checkBox(text:"Check for trailing whitespace", // text:res.getString("checkTrailSpace")
                selected: checkTrailSpace,
                actionPerformed: {
                    checkTrailSpace = !checkTrailSpace;
                },
                constraints:gbc(gridx:1, gridy:1, weightx: 0.5, fill:GridBagConstraints.HORIZONTAL, insets:[0,5,0,0]))
            checkBox(text:"Check for doubled blanks", // text:res.getString("checkDoubleSpace")
                selected: checkDoubleSpace,
                actionPerformed: {
                    checkDoubleSpace = !checkDoubleSpace;
                },
                constraints:gbc(gridx:1, gridy:2, weightx: 0.5, fill:GridBagConstraints.HORIZONTAL, insets:[0,5,0,0]))
            checkBox(text:"Check for doubled words", // text:res.getString("checkDoubleWords")
                selected: checkDoubleWords,
                actionPerformed: {
                    checkDoubleWords = !checkDoubleWords;
                },
                constraints:gbc(gridx:1, gridy:3, weightx: 0.5, fill:GridBagConstraints.HORIZONTAL, insets:[0,5,0,0]))
            checkBox(text:"Check start case", // text:res.getString("checkDiffStartCase")
                selected: checkDiffStartCase,
                actionPerformed: {
                    checkDiffStartCase = !checkDiffStartCase;
                },
                constraints:gbc(gridx:1, gridy:4, weightx: 0.5, fill:GridBagConstraints.HORIZONTAL, insets:[0,5,0,0]))
            checkBox(text:"Check punctuation at segment end", // text:res.getString("checkDiffPunctuation")
                selected: checkDiffPunctuation,
                actionPerformed: {
                    checkDiffPunctuation = !checkDiffPunctuation;
                },
                constraints:gbc(gridx:1, gridy:5, weightx: 0.5, fill:GridBagConstraints.HORIZONTAL, insets:[0,5,0,0]))
            checkBox(text:"Check for inconsistent numbers", // text:res.getString("checkNumErr")
                selected: checkNumErr,
                actionPerformed: {
                    checkNumErr = !checkNumErr;
                },
                constraints:gbc(gridx:1, gridy:6, weightx: 0.5, fill:GridBagConstraints.HORIZONTAL, insets:[0,5,0,0]))


            checkBox(text:"Check for shorter target", // text:res.getString("checkTargetShorter")
                selected: checkTargetShorter,
                actionPerformed: {
                    checkTargetShorter = !checkTargetShorter;
                },
                constraints:gbc(gridx:2, gridy:0, weightx: 0.5, fill:GridBagConstraints.HORIZONTAL, insets:[0,5,0,0]))
            checkBox(text:"Check for longer target", // text:res.getString("checkTargetLonger")
                selected: checkTargetLonger,
                actionPerformed: {
                    checkTargetLonger = !checkTargetLonger;
                },
                constraints:gbc(gridx:2, gridy:1, weightx: 0.5, fill:GridBagConstraints.HORIZONTAL, insets:[0,5,0,0]))
            checkBox(text:"Check for equal source & target", // text:res.getString("checkEqualSourceTarget")
                selected: checkEqualSourceTarget,
                actionPerformed: {
                    checkEqualSourceTarget = !checkEqualSourceTarget;
                },
                constraints:gbc(gridx:2, gridy:2, weightx: 0.5, fill:GridBagConstraints.HORIZONTAL, insets:[0,5,0,0]))
            checkBox(text:"Check for untranslated segments", // text:res.getString("checkUntranslated")
                selected: checkUntranslated,
                actionPerformed: {
                    checkUntranslated = !checkUntranslated;
                },
                constraints:gbc(gridx:2, gridy:3, weightx: 0.5, fill:GridBagConstraints.HORIZONTAL, insets:[0,5,0,0]))
            checkBox(text:"Check number of tags", // text:res.getString("checkTagNumber")
                selected: checkTagNumber,
                actionPerformed: {
                    checkTagNumber = !checkTagNumber;
                },
                constraints:gbc(gridx:2, gridy:4, weightx: 0.5, fill:GridBagConstraints.HORIZONTAL, insets:[0,5,0,0]))
            checkBox(text:"Check spaces around tags", // text:res.getString("checkTagSpace")
                selected: checkTagSpace,
                actionPerformed: {
                    checkTagSpace = !checkTagSpace;
                },
                constraints:gbc(gridx:2, gridy:5, weightx: 0.5, fill:GridBagConstraints.HORIZONTAL, insets:[0,5,0,0]))
            checkBox(text:"Check sequence of tags", // text:res.getString("checkTagOrder")
                selected: checkTagOrder,
                actionPerformed: {
                    checkTagOrder = !checkTagOrder;
                },
                constraints:gbc(gridx:2, gridy:6, weightx: 0.5, fill:GridBagConstraints.HORIZONTAL, insets:[0,5,0,0]))


            button(text:"Refresh", // text:res.getString("refresh")
                actionPerformed: {
                    QAcheck();
                    locationxy = frame.getLocation();
                    sizerw = frame.getWidth();
                    sizerh = frame.getHeight();
                    skropos = skroll.getVerticalScrollBar().getValue();
                    sort = tab.getRowSorter().getSortKeys()[0];
                    frame.setVisible(false);
                    frame.dispose();
                    interfejs(locationxy, sizerw, sizerh, skropos, sort.getColumn(), sort.getSortOrder() == javax.swing.SortOrder.DESCENDING)},
                constraints:gbc(gridx:0, gridy:7, gridwidth:3, weightx:1.0, fill:GridBagConstraints.HORIZONTAL, insets:[5,5,5,5]))
        }
    }

    frame.setLocation(locationxy);
}

def getLanguageToolInstance(ltLang) {
    return new JLanguageTool(ltLang)
}

def getLTLanguage(lang) {
   return LanguageToolNativeBridge.getLTLanguage(lang)
}

def getBiTextRules(Language sourceLang, Language targetLang) {
    return Tools.getBitextRules(sourceLang, targetLang)
}

def getRuleMatchesForEntry(sourceText, translationText) {
    if (translationText == null) {
        return null;
    }

    def ltSource = sourceLt;
    def ltTarget = targetLt;
    if (ltTarget == null || !ltTarget) {
        // LT doesn't know anything about source language
        return null;
    }

    List<RuleMatch> r = new ArrayList<RuleMatch>();
    List<RuleMatch> matches;
    if (ltSource != null && !ltTarget && bRules != null) {
        // LT knows about source and target languages both and has bitext rules
        matches = Tools.checkBitext(sourceText, translationText, ltSource, ltTarget, bRules);
    } else {
        // LT knows about target language only
        matches = ltTarget.check(translationText);
    }

    for (RuleMatch match : matches) {
        r.add(match);
    }

    return r;
}

QAcheck();
interfejs();

