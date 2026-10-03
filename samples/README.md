# Sample documents (fictional)

Six documents for a **made-up** client, Northwind Freight, and a made-up vendor,
Brightline Digital Services. They're used for the live demo, manual testing and the
evaluation set. No real client data is used anywhere in this project.

| File | Pages | What it contains |
|---|---|---|
| `Northwind_BRD_v1.2.pdf` | 6 | Business requirements: scope, roles, booking, tracking, billing, reporting, NFRs, out of scope |
| `Northwind_SOW_Brightline.pdf` | 4 | Statement of work: deliverables, milestones, fees, change requests, acceptance, warranty |
| `Northwind_Kickoff_Meeting_Notes.docx` | 2 | Kickoff decisions, action items, open questions, risks |
| `Northwind_CR-003_Invoice_Timing.pdf` | 2 | Change request moving invoice generation from 24 to 48 hours, with cost and approval |
| `Northwind_UAT_Test_Plan.pdf` | 4 | UAT schedule, test cases mapped to BR IDs, entry/exit criteria, severities |
| `Northwind_Status_Report_Week6.pdf` | 2 | Week 6 status: progress, budget, risks, decisions |

Planted on purpose:

- **A contradiction across documents:** the BRD (BR-8.1, page 5) says invoices within
  **24 hours** of e-POD; the kickoff notes (page 1) and CR-003 change it to **48 hours**.
- **Shared vocabulary:** requirement IDs such as BR-7.4 appear in both the BRD and the UAT
  plan, so retrieval has to pick the defining page.
- **Things that aren't there:** no late-delivery penalties and no UAT defect counts (UAT
  hasn't started), so those questions should be refused.

## Regenerating

The PDFs and DOCX are generated from the plain-text sources in `source/` (easy to read
and diff). After editing a source file, run from the project root:

```bash
./mvnw -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt
java -cp "$(cat target/cp.txt)" tools/GenerateSamples.java
```

PDF files embed a creation timestamp, so regenerating changes every PDF's bytes. Commit only
the ones whose source you changed (`git checkout -- <file>` restores the others).
