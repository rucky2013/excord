package com.deem.excord.controller;

import com.deem.excord.domain.EcTestcase;
import com.deem.excord.domain.EcTestfolder;
import com.deem.excord.domain.EcTestplan;
import com.deem.excord.domain.EcTestplanTestcaseMapping;
import com.deem.excord.domain.EcTeststep;
import com.deem.excord.repository.TestCaseRepository;
import com.deem.excord.repository.TestFolderRepository;
import com.deem.excord.repository.TestPlanRepository;
import com.deem.excord.repository.TestPlanTestCaseRepository;
import com.deem.excord.repository.TestStepRepository;
import com.deem.excord.util.FlashMsgUtil;
import com.deem.excord.util.HistoryUtil;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class TestCaseController {

    private static final Logger logger = LoggerFactory.getLogger(TestCaseController.class);

    @Autowired
    TestFolderRepository tfDao;

    @Autowired
    TestCaseRepository tcDao;

    @Autowired
    TestPlanTestCaseRepository tptcDao;

    @Autowired
    TestPlanRepository tpDao;

    @Autowired
    TestStepRepository tsDao;

    @Autowired
    HistoryUtil historyUtil;

    @RequestMapping(value = "/testcase", method = RequestMethod.GET)
    public String testCases(Model model, HttpSession session,
            @RequestParam(value = "nodeId", required = false, defaultValue = "2") Long nodeId) {

        FlashMsgUtil.INSTANCE.checkFlashMsg(session, model);
        EcTestfolder currenNode = tfDao.findOne(nodeId);
        EcTestfolder tempNode = currenNode;
        EcTestfolder parentNode = currenNode.getParentId();
        if (parentNode == null) {
            parentNode = currenNode;
        }

        Iterable<EcTestfolder> nodeLst = tfDao.findAllByParentIdOrderByNameAsc(tempNode);
        List<Long> childNodeLst = new ArrayList<Long>();
        for (EcTestfolder d : nodeLst) {
            if (tfDao.checkIfHasChildren(d.getId())) {
                childNodeLst.add(d.getId());
            }
        }

        List<EcTestfolder> parentNodeLst = new ArrayList<>();
        parentNodeLst.add(tempNode);
        while (tempNode.getParentId() != null) {
            parentNodeLst.add(tempNode.getParentId());
            tempNode = tempNode.getParentId();
        }
        Collections.reverse(parentNodeLst);

        //find all testcases in node.
        List<EcTestcase> testCaseLst = tcDao.findAllByFolderId(currenNode);
        if (testCaseLst == null) {
            testCaseLst = new ArrayList<EcTestcase>();
        }

        //find all test plans associated with testcases in this node.
        List<EcTestplanTestcaseMapping> tptcmapLst = tptcDao.findAllByTestFolderId(currenNode.getId());
        if (tptcmapLst == null) {
            tptcmapLst = new ArrayList<EcTestplanTestcaseMapping>();
        }

        //find all testplans which are active
        List<EcTestplan> testPlanLst = tpDao.findByEnabledOrderByIdDesc(Boolean.TRUE);
        if (testPlanLst == null) {
            testPlanLst = new ArrayList<EcTestplan>();
        }

        model.addAttribute("childNodeLst", childNodeLst);
        model.addAttribute("currentNode", currenNode);
        model.addAttribute("parentNode", parentNode);
        model.addAttribute("nodeLst", nodeLst);
        model.addAttribute("testCaseLst", testCaseLst);
        model.addAttribute("parentNodeLst", parentNodeLst);
        model.addAttribute("tptcmapLst", tptcmapLst);
        model.addAttribute("testPlanLst", testPlanLst);

        return "testcase";
    }

    @RequestMapping(value = "/testcase_save", method = RequestMethod.POST)
    public String testCaseSave(Model model, HttpSession session, HttpServletRequest request,
            @RequestParam(value = "tstepCount", required = true) Long tstepCount,
            @RequestParam(value = "tname", required = true) String tname,
            @RequestParam(value = "tdescription", required = true) String tdescription,
            @RequestParam(value = "tenabled", defaultValue = "false", required = false) Boolean tenabled,
            @RequestParam(value = "tautomated", defaultValue = "false", required = false) Boolean tautomated,
            @RequestParam(value = "tpriority", required = true) String tpriority,
            @RequestParam(value = "ttype", required = true) String ttype,
            @RequestParam(value = "tfolderId", required = true) Long tfolderId,
            @RequestParam(value = "tscriptfile", required = false) String tscriptfile,
            @RequestParam(value = "tmethod", required = false) String tmethod,
            @RequestParam(value = "tbugid", required = false) String tbugid,
            @RequestParam(value = "tlanguage", required = false) String tlanguage,
            @RequestParam(value = "tproduct", required = false) String tproduct,
            @RequestParam(value = "tfeature", required = false) String tfeature,
            @RequestParam(value = "taversion", required = false) String taversion,
            @RequestParam(value = "tdversion", required = false) String tdversion
    ) {

        EcTestfolder folder = tfDao.findOne(tfolderId);
        EcTestcase tc = new EcTestcase();
        tc.setName(tname);
        tc.setDescription(tdescription);
        tc.setEnabled(tenabled);
        tc.setAutomated(tautomated);
        tc.setPriority(tpriority);
        tc.setCaseType(ttype);
        tc.setFolderId(folder);
        tc.setTestScriptFile(tscriptfile);
        tc.setMethodName(tmethod);
        tc.setBugId(tbugid);
        tc.setLanguage(tlanguage);
        tc.setProduct(tproduct);
        tc.setFeature(tfeature);
        tc.setAddedVersion(taversion);
        tc.setDeprecatedVersion(tdversion);
        tcDao.save(tc);
        for (int i = 1; i <= tstepCount; i++) {
            EcTeststep tstep = new EcTeststep();
            tstep.setStepNumber(i);
            tstep.setDescription(request.getParameter("testStep_" + i));
            tstep.setExpected(request.getParameter("testExpected_" + i));
            tstep.setTestcaseId(tc);
            tsDao.save(tstep);

        }
        historyUtil.addHistory("Added testcase: [" + tname + "] under [" + folder.getId() + ":" + folder.getName() + "]", session, request.getRemoteAddr());
        session.setAttribute("flashMsg", "Successfully Added TestCase " + tc.getName());

        return "redirect:/testcase?nodeId=" + tfolderId;
    }

    @RequestMapping(value = "/testcase_addfolder", method = RequestMethod.POST)
    public String testCaseAddFolder(Model model, HttpSession session, HttpServletRequest request, @RequestParam(value = "folderName", required = true) String folderName, @RequestParam(value = "nodeId", required = true) Long nodeId) {

        EcTestfolder parentFolder = tfDao.findOne(nodeId);
        EcTestfolder childFolder = new EcTestfolder();
        childFolder.setName(folderName);
        childFolder.setParentId(parentFolder);
        historyUtil.addHistory("Added folder: [" + folderName + "] under [" + parentFolder.getId() + ":" + parentFolder.getName() + "]", session, request.getRemoteAddr());
        tfDao.save(childFolder);
        return "redirect:/testcase?nodeId=" + parentFolder.getId();
    }

    @RequestMapping(value = "/testcase_add", method = RequestMethod.GET)
    public String testCaseAdd(Model model, @RequestParam(value = "nodeId", required = false, defaultValue = "2") Long nodeId) {
        EcTestfolder currenNode = tfDao.findOne(nodeId);
        model.addAttribute("currentNode", currenNode);
        return "testcase_form";
    }

    @RequestMapping(value = "/testcase_testplan_link", method = RequestMethod.POST)
    public String testCaseTestPlanLink(Model model, HttpServletRequest request, HttpSession session, @RequestParam(value = "testPlanId", required = true) Long testPlanId, @RequestParam(value = "nodeId", required = true) Long nodeId, @RequestParam(value = "testcaseChk") List<Long> testcaseChkLst) {

        for (Long testCaseId : testcaseChkLst) {
            EcTestplanTestcaseMapping tptcMap = new EcTestplanTestcaseMapping();
            EcTestcase tc = tcDao.findOne(testCaseId);
            EcTestplan tp = tpDao.findOne(testPlanId);
            if (tptcDao.findByTestplanIdAndTestcaseId(tp, tc) == null) {
                //If already a mapping exists then skip
                tptcMap.setTestcaseId(tc);
                tptcMap.setTestplanId(tp);
                if (tc.getEnabled()) {
                    //Dont map disabled test cases.
                    historyUtil.addHistory("Linked TestPlan : [" + tp.getName() + "] with TestCase: [" + tc.getName() + "] ", session, request.getRemoteAddr());
                    tptcDao.save(tptcMap);
                } else {
                    logger.info("Cant link disabled test case: [{}:{}] to test plan: [{}:{}]", tc.getId(), tc.getName(), tp.getId(), tp.getName());
                }
            } else {
                logger.info("Link already exists for test case: [{}:{}] to test plan: [{}:{}]", tc.getId(), tc.getName(), tp.getId(), tp.getName());
            }
        }
        session.setAttribute("flashMsg", "Successfully Linked!");
        return "redirect:/testcase?nodeId=" + nodeId;
    }

    @RequestMapping(value = "/testcase_enable", method = RequestMethod.POST)
    public String testcaseEnable(Model model, HttpServletRequest request, HttpSession session, @RequestParam(value = "nodeId", required = true) Long nodeId, @RequestParam(value = "testcaseChk") List<Long> testcaseChkLst) {

        for (Long testCaseId : testcaseChkLst) {
            EcTestcase tc = tcDao.findOne(testCaseId);
            tc.setEnabled(true);
            historyUtil.addHistory("Enabled testcase : [" + tc.getId() + ":" + tc.getName() + "]", session, request.getRemoteAddr());
            tcDao.save(tc);
        }
        session.setAttribute("flashMsg", "Successfully enabled testcase!");
        return "redirect:/testcase?nodeId=" + nodeId;
    }

    @RequestMapping(value = "/testcase_disable", method = RequestMethod.POST)
    public String testcaseDisable(Model model, HttpServletRequest request, HttpSession session, @RequestParam(value = "nodeId", required = true) Long nodeId, @RequestParam(value = "testcaseChk") List<Long> testcaseChkLst) {

        for (Long testCaseId : testcaseChkLst) {
            EcTestcase tc = tcDao.findOne(testCaseId);
            tc.setEnabled(false);
            historyUtil.addHistory("Disabled testcase : [" + tc.getId() + ":" + tc.getName() + "]", session, request.getRemoteAddr());
            tcDao.save(tc);
        }
        session.setAttribute("flashMsg", "Successfully disabled testcase!");
        return "redirect:/testcase?nodeId=" + nodeId;
    }

    @RequestMapping(value = "/testcase_delete", method = RequestMethod.POST)
    public String testcaseDelete(Model model, HttpServletRequest request, HttpSession session, @RequestParam(value = "nodeId", required = true) Long nodeId, @RequestParam(value = "testcaseChk") List<Long> testcaseChkLst) {

        for (Long testCaseId : testcaseChkLst) {
            EcTestcase tc = tcDao.findOne(testCaseId);
            historyUtil.addHistory("Deleted testcase : [" + tc.getId() + ":" + tc.getName() + "]", session, request.getRemoteAddr());
            tcDao.delete(tc);
        }
        session.setAttribute("flashMsg", "Successfully deleted testcase!");
        return "redirect:/testcase?nodeId=" + nodeId;
    }

    @RequestMapping(value = "/testcase_deletefolder", method = RequestMethod.POST)
    public String testcaseDeleteFolder(Model model, HttpServletRequest request, HttpSession session, @RequestParam(value = "nodeId", required = true) Long nodeId) {
        EcTestfolder currenNode = tfDao.findOne(nodeId);

        if (currenNode.getParentId() != null) {
            Long parentId = currenNode.getParentId().getId();
            historyUtil.addHistory("Folder Deleted : [" + currenNode.getId() + ":" + currenNode.getName() + "]", session, request.getRemoteAddr());
            tfDao.delete(currenNode);
            session.setAttribute("flashMsg", "Successfully deleted folder!");
            return "redirect:/testcase?nodeId=" + parentId;
        } else {
            session.setAttribute("flashMsg", "Cant delete root node!");
            return "redirect:/testcase?nodeId=" + nodeId;
        }
    }

    @RequestMapping(value = "/testcase_bulkedit", method = RequestMethod.POST)
    public String testcaseBulkEdit(Model model, HttpServletRequest request, HttpSession session, @RequestParam(value = "nodeId", required = true) Long nodeId, @RequestParam(value = "testcaseChk") List<Long> testcaseChkLst) {
        String bulkTc = StringUtils.arrayToCommaDelimitedString(testcaseChkLst.toArray());
        session.setAttribute("bulkTc", bulkTc);
        model.addAttribute("nodeId", nodeId);
        return "testcase_form_bulk";
    }

    @RequestMapping(value = "/testcase_bulksave", method = RequestMethod.POST)
    public String testcaseBulkSave(Model model, HttpServletRequest request, HttpSession session,
            @RequestParam(value = "nodeId", required = true) Long nodeId,
            @RequestParam(value = "bulkTc", required = true) String bulkTc,
            @RequestParam(value = "tenabled", required = false) Boolean tenabled,
            @RequestParam(value = "tautomated", required = false) Boolean tautomated,
            @RequestParam(value = "tpriority", required = true) String tpriority,
            @RequestParam(value = "ttype", required = true) String ttype,
            @RequestParam(value = "tlanguage", required = false) String tlanguage,
            @RequestParam(value = "tproduct", required = false) String tproduct,
            @RequestParam(value = "tfeature", required = false) String tfeature,
            @RequestParam(value = "taversion", required = false) String taversion,
            @RequestParam(value = "tdversion", required = false) String tdversion) {
        String[] tcLst = StringUtils.commaDelimitedListToStringArray(bulkTc);
        for (String tc : tcLst) {
            EcTestcase tcObj = tcDao.findOne(Long.parseLong(tc));
            if (taversion != null) {
                tcObj.setAddedVersion(taversion);
            }
            if (ttype != null) {
                tcObj.setCaseType(ttype);
            }
            if (tdversion != null) {
                tcObj.setDeprecatedVersion(tdversion);
            }
            if (tenabled != null) {
                tcObj.setEnabled(tenabled);
            }
            if (tautomated != null) {
                tcObj.setAutomated(tautomated);
            }
            if (tfeature != null) {
                tcObj.setFeature(tfeature);
            }
            if (tlanguage != null) {
                tcObj.setLanguage(tlanguage);
            }
            if (tpriority != null) {
                tcObj.setPriority(tpriority);
            }
            if (tproduct != null) {
                tcObj.setProduct(tproduct);
            }
            historyUtil.addHistory("Testcase Bulk Updated : [" + tcObj.getId() + ":" + tcObj.getName() + "]", session, request.getRemoteAddr());
            tcDao.save(tcObj);
        }
        return "redirect:/testcase?nodeId=" + nodeId;
    }

    @RequestMapping(value = "/testcase_cut", method = RequestMethod.POST)
    public String testcaseCut(Model model, HttpServletRequest request, HttpSession session, @RequestParam(value = "nodeId", required = true) Long nodeId, @RequestParam(value = "testcaseChk") List<Long> testcaseChkLst) {
        String clipboardTc = StringUtils.arrayToCommaDelimitedString(testcaseChkLst.toArray());
        session.setAttribute("clipboardTc", clipboardTc);
        session.setAttribute("flashMsg", "Testcases ready to move!");
        return "redirect:/testcase?nodeId=" + nodeId;
    }

    @RequestMapping(value = "/testcase_paste", method = RequestMethod.POST)
    public String testcasePaste(Model model, HttpServletRequest request, HttpSession session, @RequestParam(value = "nodeId", required = true) Long nodeId) {
        String clipboardTc = (String) session.getAttribute("clipboardTc");

        if (clipboardTc != null) {
            String[] clipboardTcLst = StringUtils.commaDelimitedListToStringArray(clipboardTc);
            for (String tc : clipboardTcLst) {
                EcTestcase tcObj = tcDao.findOne(Long.parseLong(tc));
                EcTestfolder newNode = tfDao.findOne(nodeId);
                historyUtil.addHistory("Moved testcase : [" + tcObj.getId() + ":" + tcObj.getName() + "] from [" + tcObj.getFolderId().getId() + ":" + tcObj.getFolderId().getName() + " ] to [" + newNode.getId() + ":" + newNode.getName() + " ]", session, request.getRemoteAddr());
                tcObj.setFolderId(newNode);
                tcDao.save(tcObj);
            }
            session.setAttribute("clipboardTc", null);
            session.setAttribute("flashMsg", "Testcases moved successfully!");
        }

        return "redirect:/testcase?nodeId=" + nodeId;
    }

    @RequestMapping(value = "/testcase_renamefolder", method = RequestMethod.POST)
    public String testcaseRenameFolder(Model model, HttpServletRequest request, HttpSession session, @RequestParam(value = "nodeId", required = true) Long nodeId, @RequestParam(value = "newNodeName", required = true) String newNodeName) {

        EcTestfolder currenNode = tfDao.findOne(nodeId);
        if (currenNode.getParentId() != null) {
            currenNode.setName(newNodeName);
            historyUtil.addHistory("Folder Renamed from : [" + currenNode.getId() + ":" + currenNode.getName() + "] to [" + newNodeName + "]", session, request.getRemoteAddr());
            tfDao.save(currenNode);
            session.setAttribute("flashMsg", "Successfully renamed folder!");
        } else {
            session.setAttribute("flashMsg", "Cant rename root folder!");
        }
        return "redirect:/testcase?nodeId=" + nodeId;
    }

    @RequestMapping(value = "/testcase_clone", method = RequestMethod.POST)
    public String testcaseClone(Model model, HttpServletRequest request, HttpSession session, @RequestParam(value = "nodeId", required = true) Long nodeId, @RequestParam(value = "testcaseChk") List<Long> testcaseChkLst) {

        for (Long testCaseId : testcaseChkLst) {
            EcTestcase tc = tcDao.findOne(testCaseId);
            EcTestcase newTc = new EcTestcase();
            newTc.setAddedVersion(tc.getAddedVersion());
            newTc.setAutomated(tc.getAutomated());
            newTc.setBugId(tc.getBugId());
            newTc.setCaseType(tc.getCaseType());
            newTc.setDeprecatedVersion(tc.getDeprecatedVersion());
            newTc.setDescription(tc.getDescription());
            newTc.setEnabled(tc.getEnabled());
            newTc.setFeature(tc.getFeature());
            newTc.setFolderId(tc.getFolderId());
            newTc.setLanguage(tc.getLanguage());
            newTc.setMethodName(tc.getMethodName());
            newTc.setName(tc.getName() + "_CLONE");
            newTc.setPriority(tc.getPriority());
            newTc.setProduct(tc.getProduct());
            newTc.setTestScriptFile(tc.getTestScriptFile());
            tcDao.save(newTc);
            historyUtil.addHistory("Cloned testcase : [" + tc.getId() + ":" + tc.getName() + "]", session, request.getRemoteAddr());
            for (EcTeststep step : tc.getEcTeststepList()) {
                EcTeststep newstep = new EcTeststep();
                newstep.setDescription(step.getDescription());
                newstep.setExpected(step.getExpected());
                newstep.setStepNumber(step.getStepNumber());
                newstep.setTestcaseId(newTc);
                tsDao.save(newstep);
            }

        }
        session.setAttribute("flashMsg", "Successfully cloned!");
        return "redirect:/testcase?nodeId=" + nodeId;
    }

    @RequestMapping(value = "/testcase_export", method = RequestMethod.POST)
    public void testCaseExport(HttpServletResponse response, @RequestParam(value = "nodeId", required = true) Long nodeId, @RequestParam(value = "testcaseChk") List<Long> testcaseChkLst) {

        ServletOutputStream outputStream = null;
        List<EcTestcase> testCaseLst = tcDao.findAllByFolderId(tfDao.findOne(nodeId));
        if (testCaseLst == null) {
            testCaseLst = new ArrayList<EcTestcase>();
        }

        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("TestCases");

        Map<String, Object[]> data = new TreeMap<String, Object[]>();
        data.put("1", new Object[]{"ID", "NAME", "DESCRIPTION", "STEP", "PROCEDURE", "EXPECTED"});
        Integer idx = 2;
        for (EcTestcase tc : testCaseLst) {
            if (testcaseChkLst.contains(tc.getId())) {
                String testcaseName = tc.getName();
                String testcaseDesc = tc.getDescription();

                for (EcTeststep step : tc.getEcTeststepList()) {
                    data.put(idx.toString(), new Object[]{tc.getId().toString(), testcaseName, testcaseDesc, step.getStepNumber(), step.getDescription(), step.getExpected()});
                    testcaseDesc = "";
                    idx++;
                }
            }
        }

        Set<String> keyset = data.keySet();
        int rownum = 0;
        for (String key : keyset) {
            Row row = sheet.createRow(rownum++);
            Object[] objArr = data.get(key);
            int cellnum = 0;
            for (Object obj : objArr) {
                Cell cell = row.createCell(cellnum++);
                //All values will be Strings.
                if (obj instanceof String) {
                    cell.setCellValue((String) obj);
                } else {
                    cell.setCellValue(obj.toString());
                }
            }
        }
        try {
            response.setContentType("application/vnd.ms-excel");
            Calendar cal = Calendar.getInstance();
            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd-yyyy");
            String dateStr = sdf.format(cal.getTime());
            response.setHeader("Content-Disposition", "attachment; fileName=TestCases_Node_" + nodeId + "_" + dateStr + ".xlsx");
            outputStream = response.getOutputStream();
            workbook.write(outputStream);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {

            try {
                outputStream.flush();
                outputStream.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    @RequestMapping(value = "/testcase_upload", method = RequestMethod.POST)
    public String testCaseUpload(HttpSession session, HttpServletRequest request, @RequestParam(value = "nodeId", required = true) Long nodeId, @RequestParam(value = "file") MultipartFile file) {
        if (!file.isEmpty()) {
            try {
                byte[] bytes = file.getBytes();
                String fileName = file.getOriginalFilename();
                logger.info("Uploading file: {}", fileName);
                ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
                XSSFWorkbook workbook = new XSSFWorkbook(bis);
                XSSFSheet sheet = workbook.getSheetAt(0);
                Iterator<Row> rowIterator = sheet.iterator();
                EcTestfolder currentNode = tfDao.findOne(nodeId);
                EcTestcase tc = null;
                Integer stepNumber = 1;
                Boolean skipHeader = true;
                while (rowIterator.hasNext()) {
                    Row row = rowIterator.next();
                    if (skipHeader) {
                        skipHeader = false;
                        continue;
                    }
                    Long testId = -1L;
                    if (row.getCell(0) != null) {
                        testId = Long.parseLong(row.getCell(0).getStringCellValue());
                    }
                    String testName = row.getCell(1).getStringCellValue();
                    String testDescription = row.getCell(2).getStringCellValue();
                    String testProcedure = row.getCell(4).getStringCellValue();
                    String testExpected = row.getCell(5).getStringCellValue();
                    if (tc == null || !tc.getName().equals(testName)) {
                        stepNumber = 1;
                        //The testid should also match the folder id.
                        tc = tcDao.findByIdAndFolderId(testId, currentNode);
                        if (tc == null) {
                            tc = new EcTestcase();
                            historyUtil.addHistory("Testcase [" + testName + "] added by import", session, request.getRemoteAddr());
                        } else {
                            historyUtil.addHistory("Testcase [" + tc.getId() + ":" + tc.getName() + "] updated by import", session, request.getRemoteAddr()
                            );
                            //Delete all existing teststeps.
                            tsDao.deleteTeststepByTestcaseId(tc.getId());
                        }
                        tc.setName(testName);
                        tc.setDescription(testDescription);
                        tc.setEnabled(true);
                        tc.setFolderId(currentNode);
                        tcDao.save(tc);
                    }

                    //Just keep adding test steps.
                    EcTeststep tp = new EcTeststep();
                    tp.setStepNumber(stepNumber);
                    tp.setDescription(testProcedure);
                    tp.setExpected(testExpected);
                    tp.setTestcaseId(tc);
                    stepNumber++;
                    tsDao.save(tp);

                }
                historyUtil.addHistory("Uploaded testcase file: " + fileName, session, request.getRemoteAddr());
                session.setAttribute("flashMsg", "Successfully Imported :" + fileName);
            } catch (Exception ex) {
                session.setAttribute("flashMsg", "File upload failed!");
                ex.printStackTrace();
            }
        } else {
            session.setAttribute("flashMsg", "File is empty!");
        }
        return "redirect:/testcase?nodeId=" + nodeId;
    }

}