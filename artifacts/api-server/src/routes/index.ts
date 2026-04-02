import { Router, type IRouter } from "express";
import healthRouter from "./health";
import ariaRouter   from "./aria";

const router: IRouter = Router();

router.use(healthRouter);
router.use(ariaRouter);    // Phase 0.4 — ARIA monitoring endpoints

export default router;
