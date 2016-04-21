import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.neo4j.kernel.StoreLockException;

import java.io.*;

import ca.uwaterloo.cs.se.inconsistency.core.model2.Model;
import ca.uwaterloo.cs.se.inconsistency.core.model2.io.Model2XMLReader;
import ca.uwaterloo.cs.se.inconsistency.core.model2.so.*;

class Parser{

	private int flag = 0;
	private String input_file;
	private String input_oracle;
	private int cutype;
	
	/*
	 * cutype = 0 => already has class body and method body
	 * 			1 => has a method wrapper but no class
	 * 			2 => missing both method and class wrapper (just a bunch of statements) 
	 */
	
	public Parser(String oracle, String input_file_path) throws IOException 
	{
		String path = getPath();
		this.input_file = path + File.separator + input_file_path;
		this.input_oracle = oracle;
	}

	private String getPath() throws IOException 
	{
		Process p = Runtime.getRuntime().exec("pwd");
		BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
		String path = "";
		String s = "";
		while ((s = stdInput.readLine()) != null) {
			path = s;
		}
		return path;
	}

	public GraphDatabase getGraph()
	{
		try
		{
			GraphDatabase graphDb = new GraphDatabase(this.input_oracle);
			return graphDb;
		}
		catch(StoreLockException e)
		{
			//System.out.println("Database Locked");
			return null;
		}
	}

	private ASTParser getASTParser(String sourceCode) 
	{
		ASTParser parser = ASTParser.newParser(AST.JLS3);
		parser.setResolveBindings(true);
		parser.setStatementsRecovery(true);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(sourceCode.toCharArray());
		return parser;
	}

	private String getCodefromSnippet() throws IOException 
	{
		BufferedReader br = new BufferedReader(new FileReader(this.input_file));
		StringBuilder codeBuilder = new StringBuilder();
		String codeText = null;
		try 
		{
			String strLine = br.readLine();
			while (strLine != null) 
			{
				codeBuilder.append(strLine);
				codeBuilder.append("\n");
				strLine = br.readLine();
			}
		}
		finally
		{
			br.close();
			codeText = codeBuilder.toString();
			codeText = codeText.replace("&lt;", "<");
			codeText = codeText.replace("&gt;", ">");
			codeText = codeText.replace("&amp;", "&");
		}
		return codeText;
	}

	public CompilationUnit getCompilationUnitFromFile() throws IOException,	NullPointerException, ClassNotFoundException 
	{
		String code = getCodefromSnippet();
		ASTParser parser = getASTParser(code);
		//System.out.println("--" + code);
		ASTNode cu = (CompilationUnit) parser.createAST(null);
		//System.out.println("++ "+cu.toString());
		cutype = 0;
		if (((CompilationUnit) cu).types().isEmpty()) 
		{
			flag = 1;
			// System.out.println("Missing class body, wrapper added");
			cutype = 1;
			String s1 = "public class sample{\n" + code + "\n}";
			parser = getASTParser(s1);
			cu = parser.createAST(null);
			cu.accept(new ASTVisitor() 
			{
				public boolean visit(MethodDeclaration node) 
				{
					flag = 2;
					return false;
				}
			});
			if (flag == 1)
			{
				// System.out.println("Missing method, wrapper added");
				s1 = "public class sample{\n public void foo(){\n" + code
						+ "\n}\n}";
				cutype = 2;
				parser = getASTParser(s1);
				cu = parser.createAST(null);
			}
			if (flag == 2) 
			{
				s1 = "public class sample{\n" + code + "\n}";
				cutype = 1;
				parser = getASTParser(s1);
				cu = parser.createAST(null);
			}
		} 
		else 
		{
			// System.out.println("Has complete class and method bodies, code not modified");
			cutype = 0;
			parser = getASTParser(code);
			cu = parser.createAST(null);
		}
		return (CompilationUnit) cu;
	}

	public CompilationUnit getCompilationUnitFromString(String code) throws IOException,
	NullPointerException, ClassNotFoundException 
	{
		ASTParser parser = getASTParser(code);
		ASTNode cu = (CompilationUnit) parser.createAST(null);
		//System.out.println(cu);
		cutype = 0;
		if (((CompilationUnit) cu).types().isEmpty()) {
			flag = 1;
			// System.out.println("Missing class body, wrapper added");
			cutype = 1;
			String s1 = "public class sample{\n" + code + "\n}";
			parser = getASTParser(s1);
			cu = parser.createAST(null);
			cu.accept(new ASTVisitor() {
				public boolean visit(MethodDeclaration node) {
					flag = 2;
					return false;
				}
			});
			if (flag == 1) {
				// System.out.println("Missing method, wrapper added");
				s1 = "public class sample{\n public void foo(){\n" + code
						+ "\n}\n}";
				cutype = 2;
				parser = getASTParser(s1);
				cu = parser.createAST(null);
			}
			if (flag == 2) {
				s1 = "public class sample{\n" + code + "\n}";
				cutype = 1;
				parser = getASTParser(s1);
				cu = parser.createAST(null);
			}
		} else {
			// System.out.println("Has complete class and method bodies, code not modified");
			cutype = 0;
			parser = getASTParser(code);
			cu = parser.createAST(null);
		}
		return (CompilationUnit) cu;
	}

	int getCuType()
	{
		return cutype;
	}
}
