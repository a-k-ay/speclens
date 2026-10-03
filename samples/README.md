# Sample documents (fictional)

Three documents for a **made-up** client, Northwind Freight, and a made-up vendor,
Brightline Digital Services. They're used for the live demo, manual testing and the
evaluation set. No real client data is used anywhere in this project.

| File | Pages | What it contains |
|---|---|---|
| `Northwind_BRD_v1.2.pdf` | 6 | Business requirements: scope, roles, booking, tracking, billing, reporting, NFRs, out of scope |
| `Northwind_SOW_Brightline.pdf` | 4 | Statement of work: deliverables, milestones, fees, change requests, acceptance, warranty |
| `Northwind_Kickoff_Meeting_Notes.docx` | 2 | Kickoff decisions, action items, open questions, risks |

Planted on purpose:

- **A contradiction:** the BRD (BR-8.1, page 5) says invoices within **24 hours** of e-POD;
  the kickoff notes (page 1) change it to **48 hours**.
- **Things that aren't there:** nothing about penalties for late delivery, so asking
  about them should be refused.

## Regenerating

The PDFs and DOCX are generated from the plain-text sources in `source/` (easy to read
and diff). After editing a source file, run from the project root:

```bash
./mvnw -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt
java -cp "$(cat target/cp.txt)" tools/GenerateSamples.java
```
