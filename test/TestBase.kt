import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import org.jetbrains.kotlinx.lincheck.LoggingLevel
import org.jetbrains.kotlinx.lincheck.Options
import org.jetbrains.kotlinx.lincheck.check
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.ModelCheckingOptions
import org.jetbrains.kotlinx.lincheck.strategy.stress.StressOptions
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.io.File
import kotlin.reflect.KClass

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
abstract class TestBase(
    val sequentialSpecification: KClass<*>,
    val checkObstructionFreedom: Boolean = true,
    val scenarios: Int = 150,
    val threads: Int = 3,
    val actorsBefore: Int = 1
) {
    @Test
    fun modelCheckingTest() = try {
        ModelCheckingOptions()
            .iterations(scenarios)
            .invocationsPerIteration(10_000)
            .actorsBefore(actorsBefore)
            .threads(threads)
            .actorsPerThread(2)
            .actorsAfter(0)
            .checkObstructionFreedom(checkObstructionFreedom)
            .sequentialSpecification(sequentialSpecification.java)
            .logLevel(LoggingLevel.INFO)
            .apply { customConfiguration() }
            .check(this::class.java)
    } catch (t: Throwable) {
        uploadIncorrectSolutionToS3("model-checking")
        throw t
    }

    @Test
    fun stressTest() = try {
        StressOptions()
            .iterations(scenarios)
            .invocationsPerIteration(25_000)
            .actorsBefore(actorsBefore)
            .threads(threads)
            .actorsPerThread(2)
            .actorsAfter(0)
            .sequentialSpecification(sequentialSpecification.java)
            .logLevel(LoggingLevel.INFO)
            .apply { customConfiguration() }
            .check(this::class.java)
    } catch (t: Throwable) {
        uploadIncorrectSolutionToS3("stress")
        throw t
    }

    protected open fun Options<*, *>.customConfiguration() {}

    private fun uploadIncorrectSolutionToS3(strategy: String) = runCatching {
        val taskName = this::class.java.simpleName.replace("Test", "")
        val taskPackage = this::class.java.packageName
        val solutionFile = File("src/$taskPackage/$taskName.kt")

        val date = java.text.SimpleDateFormat("yyyy-MM-dd-HH-mm").format(java.util.Date())
        val destinationFileLocation = "$ACTIVITY/$taskName/$taskName-$date-$strategy-${kotlin.random.Random.nextInt(1000)}.kt"

        val credentials = BasicAWSCredentials("AKIA27OSP7CB7EEHHOX7", "iyFzeiqHS0amZQj79Jh1DNMy+s96f+fcJvy+BHQu")
        val s3client = AmazonS3ClientBuilder.standard()
            .withCredentials(AWSStaticCredentialsProvider(credentials))
            .withRegion(Regions.US_EAST_2)
            .build()

        s3client.putObject(S3_BUCKET_NAME, destinationFileLocation, solutionFile)
    }.let {
        if (it.isFailure) {
            System.err.println("INCORRECT IMPLEMENTATION UPLOADING HAS FAILED, PLEASE CONTACT NIKITA KOVAL TO FIX THE ISSUE")
            it.exceptionOrNull()!!.printStackTrace()
        }
    }
}

private const val ACTIVITY = "JB_AMSTERDAM_2023"
private const val S3_BUCKET_NAME = "mpp2022incorrectimplementations"