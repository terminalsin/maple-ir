package org.mapleir;

import com.google.common.collect.Streams;
import org.apache.log4j.Logger;
import org.mapleir.app.client.SimpleApplicationContext;
import org.mapleir.app.service.ApplicationClassSource;
import org.mapleir.app.service.CompleteResolvingJarDumper;
import org.mapleir.app.service.LibraryClassSource;
import org.mapleir.context.AnalysisContext;
import org.mapleir.context.BasicAnalysisContext;
import org.mapleir.context.IRCache;
import org.mapleir.deob.IPass;
import org.mapleir.deob.PassGroup;
import org.mapleir.deob.passes.rename.ClassRenamerPass;
import org.mapleir.deob.util.RenamingHeuristic;
import org.mapleir.ir.algorithms.BoissinotDestructor;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.builder.ControlFlowGraphBuilder;
import org.mapleir.ir.code.CodeUnit;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.ConstantExpr;
import org.mapleir.ir.code.expr.invoke.InvocationExpr;
import org.mapleir.ir.codegen.ControlFlowGraphDumper;
import org.mapleir.ir.utils.CFGUtils;
import org.mapleir.stdlib.util.IHasJavaDesc;
import org.mapleir.stdlib.util.InsnListUtils;
import org.mapleir.stdlib.util.JavaDesc;
import org.mapleir.stdlib.util.JavaDescSpecifier;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.topdank.byteengineer.commons.data.JarInfo;
import org.topdank.byteio.in.SingleJarDownloader;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

public class DataFlowDemoBoot {
	
	private static final Logger LOGGER = Logger.getLogger(DataFlowDemoBoot.class);
	
	public static boolean logging = false;
	private static long timer;
	private static Deque<String> sections;
	
	private static LibraryClassSource rt(ApplicationClassSource app, File rtjar) throws IOException {
		section("Loading " + rtjar.getName() + " from " + rtjar.getAbsolutePath());
		SingleJarDownloader<ClassNode> dl = new SingleJarDownloader<>(new JarInfo(rtjar));
		dl.download();
		
		return new LibraryClassSource(app, dl.getJarContents().getClassContents());
	}

	static Stream<CodeUnit> allExprStream(ControlFlowGraph cfg) {
		return cfg.vertices().stream().flatMap(Collection::stream).map(Stmt::enumerateWithSelf).flatMap(Streams::stream);
	}

	static Stream<CodeUnit> allExprStream(AnalysisContext cxt) {
		return cxt.getIRCache().values().stream().flatMap(DataFlowDemoBoot::allExprStream);
	}

	static Stream<CodeUnit> findAllRefs(AnalysisContext cxt, JavaDescSpecifier jds) {
		return allExprStream(cxt).filter(cu -> cu instanceof IHasJavaDesc).filter(cu -> jds.matches(((IHasJavaDesc) cu).getJavaDesc()));
	}

	public static void main(String[] args) throws Exception {

		sections = new LinkedList<>();
		logging = true;

		// Load input jar
		//  File f = locateRevFile(135);
		File f = new File("res/salesforce.jar");

		section("Preparing to run on " + f.getAbsolutePath());
		SingleJarDownloader<ClassNode> dl = new SingleJarDownloader<>(new JarInfo(f));
		dl.download();
		String appName = f.getName().substring(0, f.getName().length() - 4);
		ApplicationClassSource app = new ApplicationClassSource(appName, dl.getJarContents().getClassContents());
//		
// 		ApplicationClassSource app = new ApplicationClassSource("test", ClassHelper.parseClasses(CGExample.class));
//		app.addLibraries(new InstalledRuntimeClassSource(app));

		File rtjar = new File("res/rt.jar");
		File androidjar = new File("res/android.jar");
		app.addLibraries(rt(app, rtjar), rt(app, androidjar));
		section("Initialising context.");


		AnalysisContext cxt = new BasicAnalysisContext.BasicContextBuilder()
				.setApplication(app)
				.setInvocationResolver(new DefaultInvocationResolver(app))
				.setCache(new IRCache(ControlFlowGraphBuilder::build))
				.setApplicationContext(new SimpleApplicationContext(app))
				.build();

		section("Expanding callgraph and generating cfgs.");
		// IRCallTracer tracer = new IRCallTracer(cxt);
		// for(MethodNode m : cxt.getApplicationContext().getEntryPoints()) {
		// 	tracer.trace(m);
		// }



//		// todo: convert to CallGraph (as FastGraph) DFS
//		FastDirectedGraph<JavaDescVertex, JavaDescEdge> callgraph = new FastDirectedGraph<JavaDescVertex, JavaDescEdge>() {};
//		JavaDescSpecifier targetMeth = new JavaDescSpecifier("lol", "lol", ".*", JavaDesc.DescType.METHOD);
//		Set<JavaDesc> visited = new HashSet<>();
//		ArrayDeque<Pair<ControlFlowGraph, List<JavaDesc>>> analysisQueue = new ArrayDeque<>(); // desc, depth
//		Set<String> scope = new HashSet<>(Arrays.asList("lol"));
//		for (String className : scope) {
//			if (cxt.getApplication().findClassNode(className) == null)continue;
//			for (MethodNode m : cxt.getApplication().findClassNode(className).methods) {
//				ControlFlowGraph cfg = cxt.getIRCache().getFor(m);
//				analysisQueue.add(new Pair<>(cfg, Collections.singletonList(cfg.getJavaDesc())));
//			}
//		}
////		Set<String> scope = new HashSet<>(Arrays.asList("lol"));
////		for (ControlFlowGraph cfg : cxt.getIRCache().values()) {
////			if (scope.contains(cfg.getJavaDesc().owner))
////				analysisQueue.add(new Pair<>(cfg, Collections.singletonList(cfg.getJavaDesc())));
////		}
//		while (!analysisQueue.isEmpty()) {
//			Pair<ControlFlowGraph, List<JavaDesc>> ap = analysisQueue.remove();
//			ControlFlowGraph cfg = ap.getKey();
//			if (targetMeth.matches(cfg.getJavaDesc())) {
//				System.out.println("FOUND PATH " + ap.getValue());
//			}
//			if (!visited.add(cfg.getJavaDesc()))
//				continue;
////			System.out.println("Tracing " + ap.getValue());
//
//			allExprStream(cfg).filter(cu -> cu instanceof InvocationExpr).map(cu -> (InvocationExpr) cu).forEach(ie -> {
//				try {
//					for (MethodNode callTarg : ie.resolveTargets(cxt.getInvocationResolver())) {
//						if (cxt.getApplication().isLibraryClass(callTarg.owner.name)) {
//							continue;
//						}
//						List<JavaDesc> path = new ArrayList<>(ap.getValue());
//						ControlFlowGraph targCfg = cxt.getIRCache().getFor(callTarg);
//						path.add(targCfg.getJavaDesc());
//						callgraph.addEdge(new JavaDescEdge(new JavaDescVertex(cfg.getJavaDesc()), new JavaDescVertex(targCfg.getJavaDesc()), cfg.getJavaDesc()));
//						analysisQueue.add(new Pair<>(targCfg, path));
//						for (Expr arg : ie.getArgumentExprs()) {
//							ClassNode argCn = cxt.getApplication().findClassNode(arg.getType().getClassName().replace('.', '/'));
//							for (ClassNode parentCn : cxt.getApplication().getClassTree().getAllParents(argCn)) {
//								if (parentCn.name.equals("java/lang/Runnable")) {
//									MethodNode runMn = argCn.getMethod("run", "()V", false);
//									ControlFlowGraph runCfg = cxt.getIRCache().getFor(runMn);
//									List<JavaDesc> runPath = new ArrayList<>(path);
//									runPath.add(runCfg.getJavaDesc());
//									callgraph.addEdge(new JavaDescEdge(new JavaDescVertex(targCfg.getJavaDesc()), new JavaDescVertex(runCfg.getJavaDesc()), targCfg.getJavaDesc()));
//									analysisQueue.add(new Pair<>(runCfg, runPath));
//								}
//							}
//						}
//					}
//				} catch (Throwable xxx) {
////					System.err.println("Couldn't resolve " + curDesc);
//				}
//			});
//		}

		for (ClassNode cn : cxt.getApplication().iterate()) {
//			 if (!cn.name.equals("android/support/v4/media/session/MediaSessionCompat$MediaSessionImplApi18"))
//			 	continue;
			for (MethodNode m : cn.methods) {
//				 if (!m.name.equals("setRccState"))
//				 	continue;
				cxt.getIRCache().getFor(m);
			}
		}
		section0("...generated " + cxt.getIRCache().size() + " cfgs in %fs.%n", "Preparing to transform.");

		// do passes
		PassGroup masterGroup = new PassGroup("MasterController");
		for (IPass p : getTransformationPasses()) {
			masterGroup.add(p);
		}
		run(cxt, masterGroup);
		section0("...done transforming in %fs.%n", "Preparing to transform.");


		allExprStream(cxt).filter(cu -> cu instanceof ConstantExpr).map(cu -> (ConstantExpr) cu).forEach(ce -> {
			if (ce.getConstant() != null && ce.getConstant() instanceof String) {
				System.out.println(ce.getBlock().getGraph().getJavaDesc() + ", " + ce.getConstant());
			}
		});

		Scanner sc = new Scanner(System.in);
		while (true) {
			if (1+1==2)break;
			System.out.print("xref> ");
			String l = sc.nextLine();
			if (l.isEmpty())
				break;
			String[] parts = l.split("#");
			String owner = parts[0];
			String[] parts2 = parts[1].split(" ");
			String name = parts2[0];
			String desc = parts2[1];
			JavaDesc.DescType type = JavaDesc.DescType.valueOf(parts2[2].toUpperCase());
			findAllRefs(cxt, new JavaDescSpecifier(owner, name, desc, type, null)).map(cu -> (InvocationExpr) cu).forEach(ie -> {
				System.out.println("xref-> " + ie.getBlock().getGraph().getJavaDesc());
				System.out.println(ie.toString());
			});
		}

//		for(Entry<MethodNode, ControlFlowGraph> e : cxt.getIRCache().entrySet()) {
//			ControlFlowGraph cfg = e.getValue();
//			for (BasicBlock b : cfg.vertices()) {
//				for (Stmt s : b) {
//					for (CodeUnit cu : s.enumerateWithSelf()) {
//						MethodNode mn = e.getKey();
//						if (cu instanceof InvocationExpr) {
//							InvocationExpr ie = (InvocationExpr)  cu;
//							if (ie.getOwner().equals("lol") && ie.getName().equals("decrypt")) {
//								System.out.println("call to it from " + mn.owner + "#" + mn.name);
//							}
//						}
//					}
//				}
//			}
//		}

		for(Entry<MethodNode, ControlFlowGraph> e : cxt.getIRCache().entrySet()) {
			MethodNode mn = e.getKey();
			ControlFlowGraph cfg = e.getValue();
			cfg.verify();
		}

//		ArrayDeque<Pair<JavaDesc, Integer>> analysisQueue = new ArrayDeque<>(); // desc, depth
//		analysisQueue.add(new Pair<>(new JavaDesc("lol/d", "a", "[Ljava/lang/String;", JavaDesc.DescType.FIELD), 0));
//		analysisQueue.add(new Pair<>(new JavaDesc("lol/d", "b", "[Ljava/lang/String;", JavaDesc.DescType.FIELD), 0));
////		analysisQueue.add(new Pair<>(new JavaDesc("lol/d", "c", "[Ljava/lang/Integer;", JavaDesc.DescType.FIELD), 0));
////		analysisQueue.add(new Pair<>(new JavaDesc("lol/d", "d", "[Ljava/lang/Integer;", JavaDesc.DescType.FIELD), 0));
//		while (!analysisQueue.isEmpty()) {
//			Pair<JavaDesc, Integer> queuePair = analysisQueue.pop();
//			JavaDesc curDesc = queuePair.getKey();
//			int depth = queuePair.getValue();
//			if (depth > 1)
//				continue;
//			System.out.println("~ Trace queue size: " + analysisQueue.size() + " at depth " + depth);
//			for(Entry<MethodNode, ControlFlowGraph> e : cxt.getIRCache().entrySet()) {
//				MethodNode mn = e.getKey();
//				if (cxt.getApplication().isLibraryClass(mn.owner.name)) {
//					System.out.println("! Avoiding tracing library method " + curDesc);
//					continue;
//				}
//				JavaDesc mnJd = new JavaDesc(mn.owner.name, mn.name, mn.desc, JavaDesc.DescType.METHOD);
//				ControlFlowGraph cfg = e.getValue();
//				ArrayDeque<Pair<Stmt, Expr>> traceQueue = new ArrayDeque<>();
//				if (curDesc.descType.equals(JavaDesc.DescType.METHOD)) {
//					int tracedLocal = (int) curDesc.extraData;
//					if (tracedLocal >= 0) { /* not a return trace */
//						if (mn.owner.name.equals(curDesc.owner) && mn.name.equals(curDesc.name) && mn.desc.equals(curDesc.desc)) {
//							boolean found = false;
//							for (BasicBlock b : cfg.vertices()) {
//								for (Stmt s : b) {
//									for (CodeUnit cu : s.enumerateWithSelf()) {
//										if (cu instanceof VarExpr) {
//											VarExpr var = (VarExpr) cu;
//											Local l = var.getLocal();
//											if (!l.isStack() && l.getIndex() == tracedLocal) {
//												traceQueue.add(new Pair<>(s, var));
//												found = true;
//											}
//										}
//									}
//								}
//							}
//							if (found) {
//								System.out.println("! " + mnJd + " receives arg " + tracedLocal);
//							}
//						}
//					} else {
//						boolean found = false;
//						for (BasicBlock b : cfg.vertices()) {
//							for (Stmt s : b) {
//								for (CodeUnit cu : s.enumerateWithSelf()) {
//									if (cu instanceof InvocationExpr) {
//										InvocationExpr ie = (InvocationExpr) cu;
//										if (ie.getOwner().equals(curDesc.owner) && ie.getName().equals(curDesc.name) && ie.getJavaDesc().equals(curDesc.desc)) {
//											traceQueue.add(new Pair<>(s, ie));
//											found = true;
//										}
////										else if (!cxt.getApplication().isLibraryClass(ie.getOwner())) {
////											try {
////												for (MethodNode callTarg : ie.resolveTargets(cxt.getInvocationResolver())) {
////													if (callTarg.owner.name.equals(curDesc.owner) && callTarg.name.equals(curDesc.name) && callTarg.desc.equals(curDesc.desc)) {
////														traceQueue.add(new Pair<>(s, ie));
////														found = true;
////														break;
////													}
////												}
////											} catch (IllegalStateException XxX) {
////												System.err.println("Couldn't resolve " + curDesc);
////											}
////										}
//									}
//								}
//							}
//							if (found) {
//								dataFlowgraph.addEdge(new JavaDescEdge(new JavaDescVertex(mnJd), new JavaDescVertex(curDesc), mnJd));
//								System.out.println("! " + mnJd + " receives return value of " + curDesc);
//							}
//						}
//					}
//				} else if (curDesc.descType.equals(JavaDesc.DescType.FIELD)) {
//					for (BasicBlock b : cfg.vertices()) {
//						for (Stmt s : b) {
//							for (CodeUnit cu : s.enumerateWithSelf()) {
//								if (cu instanceof FieldLoadExpr) {
//									FieldLoadExpr fle = (FieldLoadExpr) cu;
//									if (fle.getOwner().equals(curDesc.owner) && fle.getName().equals(curDesc.name) && fle.getJavaDesc().equals(curDesc.desc)) {
////										System.out.println(cfg);
//										traceQueue.add(new Pair<>(s, fle));
//										System.out.println("! " + mn.owner + "#" + mn.name + mn.desc + " reads " + curDesc);
//									}
//								}
//							}
//						}
//					}
//				}
//				while (!traceQueue.isEmpty()) {
//					Pair<Stmt, Expr> traceTuple = traceQueue.pop();
//					Stmt traceStmt = traceTuple.getKey(); // data dst
//					Expr traceSrc = traceTuple.getValue(); // data src
//					if (traceStmt instanceof AbstractCopyStmt) {
//						Local toLocal = ((AbstractCopyStmt) traceStmt).getVariable().getLocal();
//						assert (toLocal instanceof VersionedLocal);
//						System.out.println(toLocal + " <- " + traceSrc);
//						for (VarExpr use : cfg.getLocals().uses.get((VersionedLocal) toLocal)) {
//							Stmt parent = use.getRootParent();
//							if (parent == null) { // phi args aren't actually children of the phi statement, TEMPORARY HACK OMFG
//								for (BasicBlock b : cfg.vertices()) {
//									for (Stmt s : b) {
//										if (s instanceof CopyPhiStmt) {
//											CopyPhiStmt cps = (CopyPhiStmt) s;
//											if (cps.getExpression().getArguments().containsValue(use)) {
//												parent = cps;
//												break;
//											}
//										}
//									}
//								}
//							}
//							if (parent == null)
//								throw new UnsupportedOperationException("we found a REAL dangler");
//							traceQueue.add(new Pair<>(parent, use)); // data flows from local to stmt
//						}
//					} else if (traceStmt instanceof FieldStoreStmt) {
//						FieldStoreStmt fss = (FieldStoreStmt) traceStmt;
//						JavaDesc jd = new JavaDesc(fss.getOwner(), fss.getName(), fss.getJavaDesc(), JavaDesc.DescType.FIELD);
//						analysisQueue.add(new Pair<>(jd, depth+1));
//						dataFlowgraph.addEdge(new JavaDescEdge(new JavaDescVertex(jd), new JavaDescVertex(curDesc), mnJd));
//						System.out.println(jd + " <- " + traceSrc);
//					} else if (traceStmt instanceof ReturnStmt) {
//						JavaDesc jd = new JavaDesc(mn.owner.name, mn.name, mn.desc, JavaDesc.DescType.METHOD, -1);
//						System.out.println(jd + " returns " + traceSrc);
//						dataFlowgraph.addEdge(new JavaDescEdge(new JavaDescVertex(jd), new JavaDescVertex(curDesc), mnJd));
//						analysisQueue.add(new Pair<>(jd, depth+1));
//					}
//
//					for (Expr traceChild : traceStmt.enumerateOnlyChildren()) {
//						if (traceChild instanceof InvocationExpr) {
//							InvocationExpr ie = (InvocationExpr) traceChild;
//							JavaDesc dbgDesc = new JavaDesc(ie.getOwner(), ie.getName(), ie.getJavaDesc(), JavaDesc.DescType.METHOD);
//							System.out.println(dbgDesc + " <- " + traceSrc);
//							if (cxt.getApplication().isLibraryClass(ie.getOwner())) {
//								System.out.println("! Avoiding tracing library method " + dbgDesc);
//							} else {
//								try {
//									Set<MethodNode> targs = ie.resolveTargets(cxt.getInvocationResolver());
//									for (MethodNode callTarg : targs) {
//										Expr[] argumentExprs = ie.getArgumentExprs();
//										for (int i = 0; i < argumentExprs.length; i++) {
//											Expr argExpr = argumentExprs[i];
//											for (Expr argSubExpr : argExpr.enumerateWithSelf()) {
//												if (argSubExpr == traceSrc) {
//													JavaDesc jd = new JavaDesc(callTarg.owner.name, callTarg.name, callTarg.desc, JavaDesc.DescType.METHOD, i);
//													dataFlowgraph.addEdge(new JavaDescEdge(new JavaDescVertex(jd), new JavaDescVertex(curDesc), mnJd));
//													analysisQueue.add(new Pair<>(jd, depth + 1));
//													System.out.println("* possible callee " + jd);
//												}
//											}
//										}
//									}
//								} catch (Exception exxxx) {
//									System.err.println("Failed to resolve " + ie.getOwner() + "# " + ie.getName() + ie.getJavaDesc());
//								}
//							}
//						}
//					}
//				}
//			}
//		}
//		dataFlowgraph.makeDotWriter().setName("depth1").add(new DotPropertyDecorator<FastGraph<JavaDescVertex, JavaDescEdge>, JavaDescVertex, JavaDescEdge>() {
//			@Override
//			public void decorateEdgeProperties(FastGraph<JavaDescVertex, JavaDescEdge> g, JavaDescVertex n, JavaDescEdge e, Map<String, Object> eprops) {
//				eprops.put("label", e.via.toString());
//			}
//		}).export();

		section("Retranslating SSA IR to standard flavour.");
		for(Entry<MethodNode, ControlFlowGraph> e : cxt.getIRCache().entrySet()) {
			MethodNode mn = e.getKey();
			// if (!mn.name.equals("openFiles"))
			// 	continue;
			ControlFlowGraph cfg = e.getValue();

			// System.out.println(cfg);
			 CFGUtils.easyDumpCFG(cfg, "pre-destruct");
			cfg.verify();

			BoissinotDestructor.leaveSSA(cfg);

			 CFGUtils.easyDumpCFG(cfg, "pre-reaalloc");
			cfg.getLocals().realloc(cfg);
			 CFGUtils.easyDumpCFG(cfg, "post-reaalloc");
			// System.out.println(cfg);
			cfg.verify();
			 System.out.println("Rewriting " + mn.name);
			(new ControlFlowGraphDumper(cfg, mn)).dump();
			 System.out.println(InsnListUtils.insnListToString(mn.instructions));
		}
		
		section("Rewriting jar.");
		dumpJar(app, dl, masterGroup, "out/rewritten.jar");
		
		section("Finished.");
	}
	
	private static void dumpJar(ApplicationClassSource app, SingleJarDownloader<ClassNode> dl, PassGroup masterGroup, String outputFile) throws IOException {
		(new CompleteResolvingJarDumper(dl.getJarContents(), app) {
			@Override
			public int dumpResource(JarOutputStream out, String name, byte[] file) throws IOException {
//				if(name.startsWith("META-INF")) {
//					System.out.println(" ignore " + name);
//					return 0;
//				}
				if(name.equals("META-INF/MANIFEST.MF")) {
					ClassRenamerPass renamer = (ClassRenamerPass) masterGroup.getPass(e -> e.is(ClassRenamerPass.class));

					if(renamer != null) {
						ByteArrayOutputStream baos = new ByteArrayOutputStream();
						BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(baos));
						BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(file)));

						String line;
						while((line = br.readLine()) != null) {
							String[] parts = line.split(": ", 2);
							if(parts.length != 2) {
								bw.write(line);
								continue;
							}

							if(parts[0].equals("Main-Class")) {
								String newMain = renamer.getRemappedName(parts[1].replace(".", "/")).replace("/", ".");
								LOGGER.info(String.format("%s -> %s%n", parts[1], newMain));
								parts[1] = newMain;
							}

							bw.write(parts[0]);
							bw.write(": ");
							bw.write(parts[1]);
							bw.write(System.lineSeparator());
						}

						br.close();
						bw.close();

						file = baos.toByteArray();
					}
				}
				return super.dumpResource(out, name, file);
			}
		}).dump(new File(outputFile));
	}
	
	private static void run(AnalysisContext cxt, PassGroup group) {
		group.accept(cxt, null, new ArrayList<>());
	}
	
	private static IPass[] getTransformationPasses() {
		RenamingHeuristic heuristic = RenamingHeuristic.RENAME_ALL;
		return new IPass[] {
//				new ConcreteStaticInvocationPass(),
//				new ClassRenamerPass(heuristic),
//				new MethodRenamerPass(heuristic),
//				new FieldRenamerPass(),
//				new CallgraphPruningPass(),
				
				// new PassGroup("Interprocedural Optimisations")
				// 	.add(new ConstantParameterPass())
				// new LiftConstructorCallsPass(),
//				 new DemoteRangesPass(),
				
				// new ConstantExpressionReorderPass(),
				// new FieldRSADecryptionPass(),
				// new ConstantParameterPass(),
//				new ConstantExpressionEvaluatorPass(),
// 				new DeadCodeEliminationPass()
				
		};
	}
	
	static File locateRevFile(int rev) {
		return new File("res/gamepack" + rev + ".jar");
	}
	
	private static Set<MethodNode> findEntries(ApplicationClassSource source) {
		Set<MethodNode> set = new HashSet<>();
		/* searches only app classes. */
		for(ClassNode cn : source.iterate())  {
			for(MethodNode m : cn.methods) {
				if((m.name.length() > 2 && !m.name.equals("<init>")) || m.instructions.size() == 0) {
					set.add(m);
				}
			}
		}
		return set;
	}
	
	private static double lap() {
		long now = System.nanoTime();
		long delta = now - timer;
		timer = now;
		return (double)delta / 1_000_000_000L;
	}
	
	public static void section0(String endText, String sectionText, boolean quiet) {
		if(sections.isEmpty()) {
			lap();
			if(!quiet)
				LOGGER.info(sectionText);
		} else {
			/* remove last section. */
			sections.pop();
			if(!quiet) {
				LOGGER.info(String.format(endText, lap()));
				LOGGER.info(sectionText);
			} else {
				lap();
			}
		}

		/* push the new one. */
		sections.push(sectionText);
	}
	
	public static void section0(String endText, String sectionText) {
		if(sections.isEmpty()) {
			lap();
			LOGGER.info(sectionText);
		} else {
			/* remove last section. */
			sections.pop();
			LOGGER.info(String.format(endText, lap()));
			LOGGER.info(sectionText);
		}

		/* push the new one. */
		sections.push(sectionText);
	}
	
	private static void section(String text) {
		section0("...took %fs.", text);
	}
}
